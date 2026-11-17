#!/bin/bash
# squeezelite.sh — ставит squeezelite + S98squeeze на уже рутованную колонку.
# Использование: ./squeezelite.sh <IP> [--name "Yandex Lite SL"] [--lms 192.168.1.131:9000]
#
# По умолчанию -s НЕ добавляется (squeezelite сам находит LMS через discovery).
# Если LMS в другой подсети или discovery блокируется — задайте --lms явно.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PAYLOAD="$HERE/payload_squeezelite"
PORT=2222

LITE_IP=""
LMS="${LMS:-}"                              # пусто = discovery
NAME="${SL_NAME:-Yandex Lite 2 SL}"

while [ $# -gt 0 ]; do
  case "$1" in
    --lms)  LMS="$2"; shift 2 ;;
    --name) NAME="$2"; shift 2 ;;
    -*)     echo "неизвестный флаг $1"; exit 1 ;;
    *)      LITE_IP="$1"; shift ;;
  esac
done
[ -n "$LITE_IP" ] || { echo "использование: $0 <IP> [--name NAME] [--lms HOST:PORT]"; exit 1; }

[ -s "$PAYLOAD/bin/squeezelite" ] || { echo "нет/пуст $PAYLOAD/bin/squeezelite — запустите save_to_payloads.sh"; exit 1; }
[ -s "$PAYLOAD/init/S98squeeze" ] || { echo "нет/пуст $PAYLOAD/init/S98squeeze"; exit 1; }

say()  { printf '\n\033[1;36m[sq]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[ERR]\033[0m %s\n' "$*" >&2; exit 1; }

SSH="ssh -p $PORT -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
$SSH "root@$LITE_IP" "true" 2>/dev/null || die "SSH до $LITE_IP:$PORT не работает — сначала root.sh"

say "Параметры:"
cat <<EOF
  LITE_IP = $LITE_IP
  LMS     = ${LMS:-<discovery>}
  NAME    = $NAME
EOF

say "sed локально: NAME=\"$NAME\"${LMS:+, LMS=$LMS}"
TMP=$(mktemp)
cp "$PAYLOAD/init/S98squeeze" "$TMP"

sed -i "s#^N=.*#N=\"$NAME\"#" "$TMP"

if [ -n "$LMS" ]; then
  LMS_IP="${LMS%:*}"
  if grep -q ' -s [0-9]' "$TMP"; then
    sed -i "s# -s [0-9]\+\.[0-9]\+\.[0-9]\+\.[0-9]\+# -s $LMS_IP#" "$TMP"
  else
    sed -i "s#setsid \$S -n \"\$N\"#setsid \$S -n \"\$N\" -s $LMS_IP#" "$TMP"
  fi
else
  sed -i "s# -s [0-9]\+\.[0-9]\+\.[0-9]\+\.[0-9]\+##g" "$TMP"
fi

sh -n "$TMP" || die "синтаксис S98squeeze сломан после sed"
echo "--- проверка результата ---"
grep -nE '^N=|setsid' "$TMP"

say "remount rw /"
$SSH "root@$LITE_IP" "mount -o remount,rw /"

say "Push squeezelite → /data/opt/bin/"
cat "$PAYLOAD/bin/squeezelite" | $SSH "root@$LITE_IP" "cat > /data/opt/bin/squeezelite && chmod 755 /data/opt/bin/squeezelite"

say "Push S98squeeze → /etc/init.d/"
cat "$TMP" | $SSH "root@$LITE_IP" "cat > /etc/init.d/S98squeeze && chmod 755 /etc/init.d/S98squeeze"
rm -f "$TMP"

say "remount ro /"
$SSH "root@$LITE_IP" "sync; mount -o remount,ro /"

say "Рестарт squeezelite"
$SSH "root@$LITE_IP" "killall squeezelite 2>/dev/null; sleep 2; /etc/init.d/S98squeeze start; sleep 5"
$SSH "root@$LITE_IP" 'PID=$(pidof squeezelite); [ -n "$PID" ] && tr "\0" " " < /proc/$PID/cmdline; echo'

echo
if [ -n "$LMS" ]; then
  say "Готово. Проверка в LMS: http://${LMS%:*}:9000 — плеер '$NAME' должен появиться."
else
  say "Готово. Проверка в LMS: http://<IP_LMS>:9000 — плеер '$NAME' должен появиться (discovery)."
  say "Если не появился — задайте --lms <IP_LMS>:9000 и повторите."
fi
