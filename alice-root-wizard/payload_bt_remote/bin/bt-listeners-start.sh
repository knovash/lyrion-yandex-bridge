#!/bin/sh
# bt-listeners-start.sh — поднять BT-стек и слушателей пультов.
# ВАЖНО: НЕ трогает вендорский D-Bus, bluealsa, maind — только добавляет свой стек.
# Читает /data/opt/bt.conf: строка "default <player> <room>" + строки "<uniq> <player> <room>".
CONF=/data/opt/bt.conf

# НЕ убиваем вендорский D-Bus / bluealsa / maind! Только наш BT-стек:
for p in $(ps | grep -E "btke[y]s-grab|btrc[v]" | awk '{print $1}'); do kill -9 $p 2>/dev/null; done
killall -9 bluetoothd bluetoothctl tail 2>/dev/null
sleep 1

# поднять ENTAWARE D-Bus (если не работает) — на отдельном сокете
if ! ps | grep -v grep | grep -q "opt/bin/dbus-daemo[n]"; then
  rm -f /opt/var/run/dbus/dbus.pid /opt/var/run/dbus/system_bus_socket
  setsid sh -c "setsid /opt/bin/dbus-daemon --system </dev/null >/dev/null 2>&1 &" >/dev/null 2>&1
  sleep 2
fi

# поднять Entware bluetoothd
hciconfig hci0 down 2>/dev/null; sleep 1; hciconfig hci0 up 2>/dev/null; sleep 1
setsid sh -c "setsid /opt/bin/bluetoothd -n -d -E -p input,hog </dev/null >/tmp/bt-boot.log 2>&1 &" >/dev/null 2>&1
sleep 4

# слушатели по конфигу
grep -v '^#' "$CONF" 2>/dev/null | while read -r u p r; do
  case "$u" in default|'') continue ;; esac
  setsid sh -c "while :; do /data/opt/bin/btkeys-listener.sh $u '$p' '$r' >> /tmp/btkeys.err 2>&1; sleep 3; done" </dev/null >/dev/null 2>&1 &
done
sleep 2
echo "стек и слушатели подняты; нажмите кнопку на пульте"

