#!/bin/sh
# bt-connect.sh (v4) — подключение BT-пульта к Лайту.
# Использование:
#   bt-connect.sh                — АВТООПРЕДЕЛЕНИЕ: подключит пульт, который сейчас
#                                  в режиме паринга (мигает LED); BT_WAIT=120 $0 — дольше
#   bt-connect.sh <MAC> [player] [room] — подключить конкретный пульт
# v4: НЕ убивает чужие tail (было: killall tail ронял ssh-сессии диагностики);
#     перед своим демоном гасит чужой супервизор демона (нет двух bluetoothd);
#     после trust — whitelist ядра (directed advertising ловится мгновенно);
#     перед слушателем гасит его старую копию (нет двух супервизоров на uniq).
# Пульты владельца (MAC):
#   G20S PRO   69:98:98:23:A7:1D   паринг: OK+Return 3-5с
#   ATV3       54:03:84:E2:39:1F   паринг: Back+Home 3-5с (сброс памяти: 10-12с)
#   Xiaomi RC  44:28:A3:9B:EA:B2   паринг: Home+Menu 3-5с
#   BPR2S      C4:2E:C3:E1:F9:C4   паринг: OK+Vol- (или OK+Return)
BCTL=/opt/bin/bluetoothctl
CONF=/data/opt/bt.conf
MAC="${1:-}"
PLAYER="${2:-}"
ROOM="${3:-}"
# дефолты плеера/комнаты — из bt.conf (строка "default <player> <room>")
if [ -f "$CONF" ]; then
  DEF=$(grep -E '^default ' "$CONF" | head -1 | sed 's/^default //')
  [ -z "$PLAYER" ] && PLAYER=$(echo "$DEF" | awk '{print $1}')
  [ -z "$ROOM" ] && ROOM=$(echo "$DEF" | awk '{print $2}')
fi
WAIT="${BT_WAIT:-30}"
SCAN=/tmp/bt-scan.out
KNOWN="69:98:98:23:A7:1D 54:03:84:E2:39:1F 44:28:A3:9B:EA:B2 C4:2E:C3:E1:F9:C4"
log(){ echo "[$(date +%H:%M:%S)] $*"; }
clean(){ sed 's/\x1b\[[0-9;]*m//g' "$SCAN" 2>/dev/null; }
findnode(){
  awk -v u="Uniq=$UNIQ" 'BEGIN{RS=""} $0 ~ u {n=split($0,L,"\n"); for(i=1;i<=n;i++) if(L[i] ~ /Handlers=/){m=split(L[i],H," "); for(j=1;j<=m;j++) if(H[j] ~ /^event/){print H[j]; exit}}}' /proc/bus/input/devices
}
killpat(){ for p in $(ps | grep -E "$1" | awk '{print $1}'); do kill -9 $p 2>/dev/null; done; }

log "0. проверяю стек (Entware bluetoothd)"
if ! ps | grep -v grep | grep -q "opt/bin/bluetooth[d].*input,hog"; then
  log "   поднимаю: гашу старый демон+его супервизор, power-cycle, новый под супервизором"
  killpat "bluetooth[d] "
  killall -9 bluetoothd bluetoothctl 2>/dev/null
  sleep 1; hciconfig hci0 down 2>/dev/null; sleep 1; hciconfig hci0 up 2>/dev/null; sleep 1
  setsid sh -c "while :; do /opt/bin/bluetoothd -n -E -p input,hog </dev/null >>/tmp/bt-connect.log 2>&1; sleep 5; done" </dev/null >/dev/null 2>&1 &
  sleep 4
fi

log "1. ищу пульт (${WAIT}с, серия пятисекундных сканов) — ЖМИТЕ КОМБО ПАРИНГА!"
rm -f $SCAN
FOUND=""
n=0
while [ $n -lt "$WAIT" ]; do
  $BCTL --timeout 5 scan le > $SCAN 2>&1
  if [ -n "$MAC" ]; then
    clean | grep -q "$MAC" && { FOUND="$MAC"; break; }
  else
    F1=$(clean | grep "00001812-0000-1000-8000-00805f9b34fb" | grep -oE "[0-9A-F:]{17}" | head -1)
    [ -n "$F1" ] && { FOUND="$F1"; break; }
    for k in $KNOWN; do
      clean | grep -q "$k" && { FOUND="$k"; break; }
    done
    [ -n "$FOUND" ] && break
  fi
  n=$((n+5))
  [ $n -lt "$WAIT" ] && log "   ищу... ещё $((WAIT - n))с"
done
if [ -z "$FOUND" ]; then
  if [ -n "$MAC" ]; then log "ПУЛЬТ $MAC за ${WAIT}с не найден (комбо/батарейка/расстояние 1-2м)"
  else log "за ${WAIT}с пульт не замечен. Комбо: G20S=OK+Return, ATV3=Back+Home, Xiaomi=Home+Menu, BPR2S=OK+Vol-. Удлинить: BT_WAIT=120 $0"; fi
  exit 1
fi
[ -n "$MAC" ] || MAC="$FOUND"
UNIQ=$(echo "$MAC" | tr 'A-Z' 'a-z')
ADAPTER=$(ls /data/opt/bluetooth | grep -E '^[0-9A-F:]{17}$' | head -1)
# имя: из бонда (надёжно), иначе из скана
NAME=$(grep '^Name=' "/data/opt/bluetooth/$ADAPTER/$MAC/info" 2>/dev/null | cut -d= -f2)
[ -n "$NAME" ] || NAME=$(clean | grep "Device $MAC" | grep -vE "UUIDs|RSSI|Connected|Paired" | tail -1 | sed "s/.*Device $MAC //" | grep -E '[А-Яа-яA-Za-z0-9]' | head -1)
log "   НАЙДЕН: ${NAME:-новый пульт} ($MAC)"

BONDED=0
[ -d "/data/opt/bluetooth/$ADAPTER/$MAC" ] && BONDED=1
if [ "$BONDED" = "1" ]; then
  log "2. уже спарен — pair пропускаю"
else
  log "2. PAIR (до 25с) — окно паринга держать открытым..."
  $BCTL --agent NoInputNoOutput pair "$MAC" >/dev/null 2>&1
  sleep 3
  if ! $BCTL info "$MAC" 2>/dev/null | sed 's/\x1b\[[0-9;]*m//g' | grep -q "Paired: yes"; then
    log "ПАРА НЕ ПРОШЛА — повторите запуск, зажав комбо до мигания и держа"
    exit 2
  fi
  log "   Paired: yes"
fi
$BCTL trust "$MAC" >/dev/null 2>&1

# whitelist ядра: directed advertising пульта ловится мгновенно (без слепых connect-серий)
if [ -x /opt/bin/btmgmt ]; then
  log "3. whitelist ядра (мгновенный автоконнект по нажатию)"
  printf 'add-device -a 2 -t 1 %s\n' "$MAC" | /opt/bin/btmgmt >/dev/null 2>&1 &
  BP=$!
  i=0; while [ $i -lt 8 ] && kill -0 $BP 2>/dev/null; do sleep 1; i=$((i+1)); done
  kill -9 $BP 2>/dev/null
fi

log "4. CONNECT-цикл до event-узла (жать кнопки!)"
i=0; NODE=""
while [ $i -lt 12 ]; do
  $BCTL --agent NoInputNoOutput connect "$MAC" >/dev/null 2>&1
  sleep 3
  NODE=$(findnode)
  [ -n "$NODE" ] && break
  i=$((i+1))
done
[ -n "$NODE" ] || { log "узел не появился (лог демона: /tmp/bt-connect.log)"; exit 3; }
log "   УЗЕЛ: /dev/input/$NODE"

# три бипа — пульт подключён (и новый, и уже спаренный)
BEEP=/data/opt/sounds/beep.wav
[ -s "$BEEP" ] && for b in 1 2 3; do aplay -q "$BEEP" >/dev/null 2>&1; sleep 0.3; done

log "5. бонд -> вендор-хранилище"
[ "$BONDED" = "1" ] || cp -r "/data/opt/bluetooth/$ADAPTER/$MAC" "/data/bluez_data/lib/bluetooth/$ADAPTER/" 2>/dev/null

log "6. слушатель btkeys-grab (бип + /cmd, player=$PLAYER room=$ROOM)"
# погасить СТАРУЮ копию слушателя этого uniq (обе эпохи имён) — нет драки за EVIOCGRAB
killpat "btke[y]s-(grab|listener)[.]sh $UNIQ|btrc[v] $UNIQ"
# записать пульт в bt.conf (заменить строку с этим uniq или добавить; server/default не трогаем)
grep -v " $UNIQ " "$CONF" 2>/dev/null > /tmp/bt.conf.new
[ -f "$CONF" ] || echo "default $PLAYER $ROOM" > /tmp/bt.conf.new
echo "$UNIQ $PLAYER $ROOM" >> /tmp/bt.conf.new
cp /tmp/bt.conf.new "$CONF"
setsid sh -c "while :; do /data/opt/bin/btkeys-grab.sh $UNIQ '$PLAYER' '$ROOM' >> /tmp/btkeys.err 2>&1; sleep 3; done" </dev/null >/dev/null 2>&1 &
sleep 2

log "ГОТОВО! Пульт $MAC подключён, узел $NODE, слушатель работает."
log "Кнопки: ◀▶=треки, OK=пауза, VOL±, Home=switch_here, Search=что играет, цифры=избранное."
log "Ребут станции поднимет всё сам (S99btkeys); вручную: bt-listeners-start.sh"
