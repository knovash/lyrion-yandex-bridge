#!/bin/sh
# bt-remove.sh — удалить спаренный BT-пульт (бонд из обоих хранилищ + слушатель).
# Использование:
#   bt-remove.sh            — интерактивное меню (показать спаренные, выбрать номер/MAC)
#   bt-remove.sh <MAC>      — удалить конкретный
#   bt-remove.sh all        — удалить ВСЕ пульты (осторожно!)
BCTL=/opt/bin/bluetoothctl
ADAPTER=$(ls /data/opt/bluetooth | grep -E '^[0-9A-F:]{17}$' | head -1)
ST1="/data/opt/bluetooth/$ADAPTER"
ST2="/data/bluez_data/lib/bluetooth/$ADAPTER"
log(){ echo "[$(date +%H:%M:%S)] $*"; }

remove_one(){
  M="$1"
  U=$(echo "$M" | tr 'A-Z' 'a-z')
  NAME=$(grep '^Name=' "$ST1/$M/info" 2>/dev/null | cut -d= -f2)
  log "удаляю: ${NAME:-?} ($M)"
  $BCTL disconnect "$M" >/dev/null 2>&1
  $BCTL remove "$M" >/dev/null 2>&1
  rm -rf "$ST1/$M" "$ST2/$M" 2>/dev/null
  # гасим слушатель этого пульта (supervisor + btkeys-listener + btrcv)
  for p in $(ps | grep -E "btke[y]s-grab.sh $U|btrc[v] $U" | awk '{print $1}'); do kill -9 $p 2>/dev/null; done
  for p in $(ps | grep -E "btke[y]s-grab.sh $U" | awk '{print $1}'); do kill -9 $p 2>/dev/null; done
  log "   удалено (бонды + слушатель)"
}

# --- список спаренных ---
LIST=$(ls "$ST1" 2>/dev/null | grep -E '^[0-9A-F:]{17}$')
[ -n "$LIST" ] || { echo "спаренных пультов нет"; exit 0; }

if [ "$1" = "all" ]; then
  for m in $LIST; do remove_one "$m"; done
  echo "готово: все пульты удалены"
  exit 0
fi

if [ -n "$1" ]; then
  echo "$LIST" | grep -qx "$1" || { echo "$1 не найден в спаренных"; exit 1; }
  remove_one "$1"
  exit 0
fi

# --- интерактивное меню ---
echo "Спаренные BT-пульты станции:"
i=0
for m in $LIST; do
  i=$((i+1))
  NAME=$(grep '^Name=' "$ST1/$m/info" 2>/dev/null | cut -d= -f2)
  printf "  %d) %-24s %s\n" "$i" "${NAME:-?}" "$m"
done
echo "  a) УДАЛИТЬ ВСЕ"
printf "Выберите номер (1-%d), a или MAC (Enter — отмена): " "$i"
read -r ANS
[ -n "$ANS" ] || { echo "отмена"; exit 0; }
if [ "$ANS" = "a" ]; then
  for m in $LIST; do remove_one "$m"; done
elif echo "$LIST" | grep -qx "$ANS"; then
  remove_one "$ANS"
elif [ "$ANS" -ge 1 ] 2>/dev/null && [ "$ANS" -le "$i" ] 2>/dev/null; then
  M=$(echo "$LIST" | sed -n "${ANS}p")
  remove_one "$M"
else
  echo "не понял выбор: $ANS"
  exit 1
fi
echo "готово"
