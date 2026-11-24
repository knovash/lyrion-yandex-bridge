#!/bin/bash
# bt_remote.sh (v4) — установка BT-пультов на рутованную Станцию Лайт.
# Использование: ./bt_remote.sh <IP>              — АВТО: плеер по комнате колонки
#                ./bt_remote.sh <IP> --room <Комната> [--player <Имя>] [--bridge H:P]
# ПРАВИЛО ВЛАДЕЛЬЦА: пульт рулит LMS-плеером в комнате, где стоит колонка;
# если LMS-плеера в комнате нет — пульт рулит squeezelite самой станции.
# Комната: --room > commandslistener станции; плеер: --player > status.json
# моста (по комнате) > squeezelite станции (имя из cmdline, дефолт задан).
#
# Что делает:
#   1) Заливает yse и ставит Entware (yse bootstrap, если его ещё нет) + bluez
#   2) Заливает btrcv + btkeys-grab.sh + bt-connect.sh + bt-remove.sh
#      + bt-listeners-start.sh + beep.wav (с SHA-сверкой каждой заливки!)
#   3) Создаёт хранилище бондов (/data/opt/bluetooth) + симлинк
#   4) Настраивает Entware D-Bus (machine-id + сокет; вендорский не трогаем)
#   5) Пишет bt.conf: server <bridge> + default <player> <room>
#      + тюнинг-конфиги BlueZ (input.conf/main.conf, эталон .115)
#   6) Ставит S99btkeys в /etc/init.d — автозапуск стека при загрузке станции
#   7) Запускает стек + слушателей
#
# НЕ трогает: вендорский D-Bus, bluealsa, maind — Алиса продолжает работать.
# v3: САМОДОСТАТОЧНО «из архива» — yse входит в payload и сам разворачивает
#     Entware (нужен только интернет НА СТАНЦИИ); SHA-сверка заливок (грабля
#     «жпун»); bridge в bt.conf (смена сервера = одна строка); S99-автозапуск.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PAYLOAD="$HERE/payload_bt_remote"
PORT=2222

LITE_IP=""
PLAYER=""
ROOM=""
BRIDGE="${BRIDGE:-192.168.1.131:8888}"

while [ $# -gt 0 ]; do
  case "$1" in
    --player)  PLAYER="$2"; shift 2 ;;
    --room)    ROOM="$2"; shift 2 ;;
    --bridge)  BRIDGE="$2"; shift 2 ;;
    -*)        echo "неизвестный флаг $1"; exit 1 ;;
    *)         LITE_IP="$1"; shift ;;
  esac
done

[ -n "$LITE_IP" ] || { echo "использование: $0 <IP> [--player <Имя>] [--room <Комната>] [--bridge ip:8888]"; exit 1; }
# --player/--room НЕ обязательны: без них — автоопределение (блок 0/7 ниже)

say()  { printf '\n\033[1;36m[bt]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[ERR]\033[0m %s\n' "$*" >&2; exit 1; }

SSH="ssh -p $PORT -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"

for f in bin/yse bin/btrcv bin/btkeys-grab.sh bin/bt-connect.sh bin/bt-remove.sh bin/bt-listeners-start.sh init/S99btkeys sounds/beep.wav; do
  [ -f "$PAYLOAD/$f" ] || die "нет $PAYLOAD/$f — проверьте payload_bt_remote"
done

# --- 0/7: автоопределение комнаты/плеера (режим «просто IP») ---
# NB: этот блок писан python-RAW-строками: обычные строки ели \0/\1 (окталы
#     -> байты 0x00/0x01 -> «unterminated quoted string» на станции).
if [ -z "$ROOM" ] || [ -z "$PLAYER" ]; then
  say "0/7: автоопределение (правило: LMS-плеер комнаты колонки, иначе squeezelite станции)"
  if [ -z "$ROOM" ]; then
    # оба имени: свежие станции — commandslistener.sh, легаси — whatplays.sh
    ROOM=$($SSH "root@$LITE_IP" 'grep -hE "^ROOM=" /data/opt/bin/commandslistener.sh /data/opt/bin/whatplays.sh 2>/dev/null | head -1 | sed "s/^ROOM=.//; s/.$//"' | tr -d '\r\n')
    [ -n "$ROOM" ] && say "  комната (со станции): $ROOM"
  fi
  if [ -z "$PLAYER" ] && [ -n "$ROOM" ]; then
    # LMS-плеер этой комнаты — из status.json моста sa_client (как room_player)
    PLAYER=$(curl -s -m 6 "http://$BRIDGE/status.json" 2>/dev/null | sed 's/},/}\n/g' \
      | grep "\"room\":\"$ROOM\"" | sed -n 's/.*"name":"\([^"]*\)".*/\1/p' | head -1)
    [ -n "$PLAYER" ] && say "  LMS-плеер комнаты «$ROOM»: $PLAYER"
  fi
  if [ -z "$PLAYER" ]; then
    # LMS-плеера в комнате нет → пульт рулит squeezelite самой станции
    PLAYER=$($SSH "root@$LITE_IP" '
      N=""
      Q=$(pidof squeezelite)
      [ -n "$Q" ] && N=$(tr "\0" "\n" < /proc/$Q/cmdline | sed -n "/^-n$/{n;p;}")
      if [ -z "$N" ] && [ -f /etc/init.d/S98squeeze ]; then
        N=$(sed -n "s/.*NAME=.//;s/.$//p" /etc/init.d/S98squeeze | head -1)
        [ -z "$N" ] && N=$(sed -n "s/.*-n \"\([^\"]*\)\".*/\1/p" /etc/init.d/S98squeeze | head -1)
      fi
      [ -z "$N" ] && N="Yandex Lite 2 SL"
      echo "$N"
    ' | tr -d '\r\n')
    say "  LMS-плеера в комнате нет → пульт рулит squeezelite станции: $PLAYER"
  fi
  [ -n "$PLAYER" ] || die "не смог определить плеер — укажите --player <Имя LMS-плеера>"
fi

# заливка с SHA-сверкой (грабля «жпун#!/bin/sh» из истории — больше не пройдёт)
up(){
  local f="$1" dst="/data/opt/$1" L R
  cat "$PAYLOAD/$f" | $SSH "root@$LITE_IP" "cat > $dst && chmod 755 $dst"
  L=$(sha256sum "$PAYLOAD/$f" | cut -d' ' -f1)
  R=$($SSH "root@$LITE_IP" "sha256sum $dst 2>/dev/null | cut -d' ' -f1" | tr -d '\r\n ')
  [ "$L" = "$R" ] && say "  ✓ $dst (sha ok)" || die "SHA НЕ СХОДИТСЯ: $dst — файл битый, перезапустите установку"
}

say "1/7: yse + Entware + bluez"
up bin/yse
if ! $SSH "root@$LITE_IP" 'ls /opt/bin/opkg >/dev/null 2>&1'; then
  say "Entware нет — yse bootstrap (нужен интернет на станции, 1-3 мин)..."
  $SSH "root@$LITE_IP" 'sh /data/opt/bin/yse bootstrap 2>&1 | tail -8' || die "yse bootstrap не прошёл (интернет на станции?)"
fi
$SSH "root@$LITE_IP" '
  ls /opt/bin/opkg >/dev/null 2>&1 || { echo "Entware не поднялся" >&2; exit 1; }
  if ! ls /opt/bin/bluetoothd >/dev/null 2>&1; then
    /opt/bin/opkg update >/dev/null 2>&1
    /opt/bin/opkg install bluez-daemon bluez-libs bluez-utils 2>&1 | tail -3
  else
    echo "bluez уже установлен"
  fi
' || die "Entware bluez не установить"

say "2/7: Заливка файлов (с SHA-сверкой)"
for f in bin/btrcv bin/btkeys-grab.sh bin/bt-connect.sh bin/bt-remove.sh bin/bt-listeners-start.sh; do
  up "$f"
done
cat "$PAYLOAD/sounds/beep.wav" | $SSH "root@$LITE_IP" "mkdir -p /data/opt/sounds && cat > /data/opt/sounds/beep.wav"
say "  ✓ /data/opt/sounds/beep.wav"
# легаси-имя слушателя (до унификации) — убрать, чтобы не путалось
$SSH "root@$LITE_IP" 'rm -f /data/opt/bin/btkeys-listener.sh; true' >/dev/null 2>&1 || true

say "3/7: Хранилище бондов + симлинк"
$SSH "root@$LITE_IP" '
  ADAPTER=$(hciconfig hci0 2>/dev/null | grep -oE "[0-9A-F:]{17}" | head -1)
  [ -n "$ADAPTER" ] || { echo "hci0 не найден" >&2; exit 1; }
  mkdir -p /data/opt/bluetooth/$ADAPTER
  mount -o remount,rw / 2>/dev/null
  [ -L /var/lib/bluetooth ] || { rm -rf /var/lib/bluetooth; ln -sf /data/opt/bluetooth /var/lib/bluetooth; }
  mount -o remount,ro / 2>/dev/null
  echo "хранилище: /data/opt/bluetooth/$ADAPTER"
' || die "хранилище не создано"

say "4/7: Entware D-Bus"
$SSH "root@$LITE_IP" '
  mkdir -p /opt/var/lib/dbus /opt/var/run/dbus
  [ -s /opt/var/lib/dbus/machine-id ] || {
    DU=/opt/bin/dbus-uuidgen; [ -x "$DU" ] || DU=dbus-uuidgen
    $DU --ensure=/opt/var/lib/dbus/machine-id 2>/dev/null
  }
  # НЕ трогаем вендорский D-Bus на /var/run — только наш на /opt/var/run
  if ! ps | grep -v grep | grep -q "opt/bin/dbus-daemo[n]"; then
    rm -f /opt/var/run/dbus/dbus.pid /opt/var/run/dbus/system_bus_socket
    setsid sh -c "setsid /opt/bin/dbus-daemon --system </dev/null >/dev/null 2>&1 &" >/dev/null 2>&1
    sleep 2
  fi
'
$SSH "root@$LITE_IP" 'ls /opt/var/run/dbus/system_bus_socket >/dev/null 2>&1' || die "Entware D-Bus не поднялся (/opt/var/run/dbus)"

say "5/7: bt.conf (server + default; uniq-строки существующих пультов сохраняются)"
$SSH "root@$LITE_IP" "
  T=/tmp/bt.conf.new
  if [ -f /data/opt/bt.conf ]; then grep -vE '^(server|default) ' /data/opt/bt.conf > \$T; else : > \$T; fi
  printf 'server %s\ndefault %s %s\n' '$BRIDGE' '$PLAYER' '$ROOM' > /data/opt/bt.conf
  cat \$T >> /data/opt/bt.conf && rm -f \$T
  cat /data/opt/bt.conf
"

say "5a/7: тюнинг-конфиги BlueZ (эталон .115 — без них BLE HID-пары падают)"
$SSH "root@$LITE_IP" 'mkdir -p /opt/etc/bluetooth
printf "[General]\nUserspaceHID = true\nLEAutoSecurity = true\nClassicBondedOnly = false\nIdleTimeout = 0\n" > /opt/etc/bluetooth/input.conf
printf "[General]\nControllerMode = dual\nTemporaryTimeout = 0\nExperimental = true\nKernelExperimental = true\nPrivacy = device\n\n[GATT]\nCache = off\n" > /opt/etc/bluetooth/main.conf
echo "  input.conf: $(grep -c = /opt/etc/bluetooth/input.conf) ключей; main.conf: ControllerMode/TemporaryTimeout/Cache ok"'

say "6/7: S99btkeys → /etc/init.d (автозапуск при загрузке)"
if cat "$PAYLOAD/init/S99btkeys" | $SSH "root@$LITE_IP" '
  mount -o remount,rw / 2>/dev/null || { echo "remount rw не вышел" >&2; exit 1; }
  cat > /etc/init.d/S99btkeys && chmod 755 /etc/init.d/S99btkeys
  sync; mount -o remount,ro / 2>/dev/null
  sh -n /etc/init.d/S99btkeys && echo "S99btkeys ок (sh -n)"
'; then :; else
  warn "S99btkeys не установлен (rootfs RO?) — автозапуска не будет, стек поднимается вручную: bt-listeners-start.sh"
fi

say "7/7: Запуск стека"
$SSH "root@$LITE_IP" 'sh /data/opt/bin/bt-listeners-start.sh' 2>/dev/null

cat <<EOF

============================================================
ГОТОВО! BT-стек развёрнут на $LITE_IP (bridge: $BRIDGE).

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
        Home=переключи сюда, 1-9=избранное

Ребут станции: стек и слушатели поднимутся сами (S99btkeys, ~25с).
Смена bridge-сервера: правка ОДНОЙ строки "server" в /data/opt/bt.conf
  и затем: sh /data/opt/bin/bt-listeners-start.sh
============================================================

EOF
