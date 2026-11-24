#!/bin/sh
# bt-listeners-start.sh v3 — поднять BT-стек и слушателей пультов (идемпотентно).
# ВАЖНО: НЕ трогает вендорский D-Bus, bluealsa, maind — Алиса продолжает работать.
# Читает /data/opt/bt.conf:
#   server <ip[:port]>          — bridge sa_client (дефолт зашит в btkeys-grab.sh)
#   default <player> <room>     — дефолт для новых пультов
#   <uniq-mac> <player> <room>  — по слушателю на пульт
# v3: kill-паттерны матчат ОБЕ эпохи имён (btkeys-grab и btkeys-listener) + супервизоры
#     (иначе дубли супервизоров/драка за EVIOCGRAB); демон под супервизором (упал —
#     поднимется через 5с) и БЕЗ -d (лог не растёт); D-Bus не поднялся — честный отказ.
CONF=/data/opt/bt.conf

# гасим старые слушатели + супервизоры + демон (обе эпохи имён)
for p in $(ps | grep -E "btke[y]s-(grab|listener)[.]sh|btrc[v]|bluetooth[d] " | awk '{print $1}'); do
  kill -9 $p 2>/dev/null
done
killall -9 bluetoothd bluetoothctl 2>/dev/null
sleep 1

# трим лога демона (старый -d-режим оставлял сотни КБ)
[ -f /tmp/bt-boot.log ] && [ "$(wc -c < /tmp/bt-boot.log)" -gt 262144 ] && \
  { tail -c 131072 /tmp/bt-boot.log > /tmp/bt-boot.log.t 2>/dev/null && mv /tmp/bt-boot.log.t /tmp/bt-boot.log; }

# Entware D-Bus (свой сокет /opt/var/run/dbus; вендорский /var/run/dbus НЕ трогаем!)
if ! ps | grep -v grep | grep -q "opt/bin/dbus-daemo[n]"; then
  rm -f /opt/var/run/dbus/dbus.pid /opt/var/run/dbus/system_bus_socket
  setsid sh -c "setsid /opt/bin/dbus-daemon --system </dev/null >/dev/null 2>&1 &" >/dev/null 2>&1
  sleep 2
fi
if ! ls /opt/var/run/dbus/system_bus_socket >/dev/null 2>&1; then
  echo "ОШИБКА: Entware D-Bus не поднялся (/opt/var/run/dbus) — стек не стартует"
  exit 1
fi

# адаптер + демон под супервизором (упал — поднимется через 5с)
hciconfig hci0 down 2>/dev/null; sleep 1; hciconfig hci0 up 2>/dev/null; sleep 1
setsid sh -c "while :; do /opt/bin/bluetoothd -n -E -p input,hog </dev/null >>/tmp/bt-boot.log 2>&1; sleep 5; done" </dev/null >/dev/null 2>&1 &
sleep 4

# слушатели по конфигу (по одному на uniq)
echo "стек поднят, слушатели:"
grep -v '^#' "$CONF" 2>/dev/null | while read -r u p r; do
  case "$u" in server|default|'') continue ;; esac
  setsid sh -c "while :; do /data/opt/bin/btkeys-grab.sh $u '$p' '$r' >> /tmp/btkeys.err 2>&1; sleep 3; done" </dev/null >/dev/null 2>&1 &
  echo "  $u → $p${r:+ ($r)}"
done
sleep 2
echo "готово. Новый пульт: /data/opt/bin/bt-connect.sh. Проверка: ps | grep btrcv"
