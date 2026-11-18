#!/bin/sh
# bt_remote.sh — установка BT-пультов на рутованную Станцию Лайт.
# Использование: ./bt_remote.sh <IP> --player <ИмяПлеераLMS> --room <Комната>
#                [--bridge 192.168.1.131:8888] [--lms 192.168.1.131:9000]
#
# Что делает:
#   1) Устанавливает Entware bluez (bluetoothd 5.87 с input,hog)
#   2) Копирует btrcv + btkeys-listener.sh + bt-connect.sh + bt-remove.sh
#      + bt-listeners-start.sh + beep.wav
#   3) Создаёт хранилище бондов (/data/opt/bluetooth) + симлинк
#   4) Настраивает Entware D-Bus (machine-id + сокет)
#   5) Записывает bt.conf (плеер/комната)
#   6) Запускает стек + слушателей
#   7) Подсказывает «зажмите паринг на пульте»
#
# НЕ трогает: вендорский D-Bus, bluealsa, maind — Алиса продолжает работать.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PAYLOAD="$HERE/payload_bt_remote"
PORT=2222

LITE_IP=""
PLAYER=""
ROOM=""
BRIDGE="${BRIDGE:-192.168.1.131:8888}"
LMS="${LMS:-192.168.1.131:9000}"

while [ $# -gt 0 ]; do
  case "$1" in
    --player)  PLAYER="$2"; shift 2 ;;
    --room)    ROOM="$2"; shift 2 ;;
    --bridge)  BRIDGE="$2"; shift 2 ;;
    --lms)     LMS="$2"; shift 2 ;;
    -*)        echo "неизвестный флаг $1"; exit 1 ;;
    *)         LITE_IP="$1"; shift ;;
  esac
done

[ -n "$LITE_IP" ] || { echo "использование: $0 <IP> --player <ИмяПлеера> --room <Комната>"; exit 1; }
[ -n "$PLAYER" ]  || { echo "--player обязателен (имя LMS-плеера, например: HomePod1)"; exit 1; }
[ -n "$ROOM" ]    || { echo "--room обязателен (например: Гостиная)"; exit 1; }

say()  { printf '\n\033[1;36m[bt]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[ERR]\033[0m %s\n' "$*" >&2; exit 1; }

SSH="ssh -p $PORT -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"

for f in bin/btrcv bin/btkeys-listener.sh bin/bt-connect.sh bin/bt-remove.sh bin/bt-listeners-start.sh sounds/beep.wav; do
  [ -f "$PAYLOAD/$f" ] || die "нет $PAYLOAD/$f — запустите save_to_payloads.sh"
done

say "1/6: Установка Entware bluez"
$SSH "root@$LITE_IP" '
  if ! ls /opt/bin/opkg >/dev/null 2>&1; then
    echo "Entware не установлен — установите через yse: /data/opt/bin/yse update" >&2
    exit 1
  fi
  if ! ls /opt/bin/bluetoothd >/dev/null 2>&1; then
    /opt/bin/opkg update >/dev/null 2>&1
    /opt/bin/opkg install bluez-daemon bluez-libs bluez-utils 2>&1 | tail -3
  else
    echo "bluez уже установлен"
  fi
' || die "Entware bluez не установить"

say "2/6: Заливка файлов"
for f in bin/btrcv bin/btkeys-listener.sh bin/bt-connect.sh bin/bt-remove.sh bin/bt-listeners-start.sh; do
  cat "$PAYLOAD/$f" | $SSH "root@$LITE_IP" "cat > /data/opt/$f && chmod 755 /data/opt/$f" 2>/dev/null
  say "  ✓ /data/opt/$f"
done
cat "$PAYLOAD/sounds/beep.wav" | $SSH "root@$LITE_IP" "mkdir -p /data/opt/sounds && cat > /data/opt/sounds/beep.wav" 2>/dev/null
say "  ✓ /data/opt/sounds/beep.wav"

say "3/6: Хранилище бондов + симлинк"
$SSH "root@$LITE_IP" '
  ADAPTER=$(hciconfig hci0 2>/dev/null | grep -oE "[0-9A-F:]{17}" | head -1)
  [ -n "$ADAPTER" ] || { echo "hci0 не найден"; exit 1; }
  mkdir -p /data/opt/bluetooth/$ADAPTER
  mount -o remount,rw / 2>/dev/null
  [ -L /var/lib/bluetooth ] || { rm -rf /var/lib/bluetooth; ln -sf /data/opt/bluetooth /var/lib/bluetooth; }
  mount -o remount,ro / 2>/dev/null
  echo "хранилище: /data/opt/bluetooth/$ADAPTER"
' || die "хранилище не создано"

say "4/6: Entware D-Bus"
$SSH "root@$LITE_IP" '
  mkdir -p /opt/var/lib/dbus /opt/var/run/dbus
  [ -s /opt/var/lib/dbus/machine-id ] || dbus-uuidgen --ensure=/opt/var/lib/dbus/machine-id 2>/dev/null
  # НЕ трогаем вендорский D-Bus на /var/run — только наш на /opt/var/run
  if ! ps | grep -v grep | grep -q "opt/bin/dbus-daemo[n]"; then
    rm -f /opt/var/run/dbus/dbus.pid /opt/var/run/dbus/system_bus_socket
    setsid sh -c "setsid /opt/bin/dbus-daemon --system </dev/null >/dev/null 2>&1 &" >/dev/null 2>&1
    sleep 2
  fi
  ls /opt/var/run/dbus/system_bus_socket >/dev/null 2>&1 && echo "D-Bus ок" || echo "D-Bus не поднялся"
'

say "5/6: bt.conf"
$SSH "root@$LITE_IP" "printf 'default $PLAYER $ROOM\n' > /data/opt/bt.conf && cat /data/opt/bt.conf"

say "6/6: Запуск стека"
$SSH "root@$LITE_IP" 'sh /data/opt/bin/bt-listeners-start.sh' 2>/dev/null

cat <<EOF

============================================================
ГОТОВО! BT-стек развёрнут на $LITE_IP.

ДЛЯ ПОДКЛЮЧЕНИЯ ПУЛЬТА:
  ssh -p $PORT root@$LITE_IP
  /data/opt/bin/bt-connect.sh
  → зажмите комбо паринга на пульте (LED мигает)
  → колонка пикнет 3 раза = подключено!

Комбо паринга:
  G20S PRO:  OK + Return (3-5с)
  ATV3:      Back + Home (3-5с)
  Xiaomi RC: Home + Menu (3-5с)
  BPR2S:     OK + Vol- (3-5с)

Кнопки: OK=play/pause, VOL±=громкость, ◀▶=треки,
        ▲▼=каналы, Home=переключи сюда, 1-9=избранное

После ребута станции: sh /data/opt/bin/bt-listeners-start.sh
============================================================

EOF