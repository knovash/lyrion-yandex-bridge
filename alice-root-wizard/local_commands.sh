#!/bin/bash
# local_commands.sh (v2) — ставит commandslistener (экс-whatplays v5) + alice_ipc_mon + tcpcat + S97cmdlistener.
# v2: файл на живых станциях переименован commandslistener.sh → commandslistener.sh (содержимое то же);
#     при установке гасятся процессы ОБЕИХ имён (миграция без дублей).
# Использование: ./local_commands.sh <IP> --room <Имя> --aliceid <UUID>
#                [--player "Имя LMS-плеера"] [--bridge 192.168.1.131:8888] [--lms 192.168.1.131:9000]
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PAYLOAD="$HERE/payload_local_commands"
PORT=2222

LITE_IP=""
ROOM=""
ALICEID=""
PLAYER=""                                # пусто = не трогать announce() ("Yandex Lite 2 SL")
BRIDGE="${BRIDGE:-192.168.1.131:8888}"
LMS="${LMS:-192.168.1.131:9000}"

while [ $# -gt 0 ]; do
  case "$1" in
    --room)    ROOM="$2"; shift 2 ;;
    --aliceid) ALICEID="$2"; shift 2 ;;
    --player)  PLAYER="$2"; shift 2 ;;
    --bridge)  BRIDGE="$2"; shift 2 ;;
    --lms)     LMS="$2"; shift 2 ;;
    -*)        echo "неизвестный флаг $1"; exit 1 ;;
    *)         LITE_IP="$1"; shift ;;
  esac
done

[ -n "$LITE_IP" ] || { echo "использование: $0 <IP> --room <Имя> --aliceid <UUID> [--player <Имя плеера>]"; exit 1; }
[ -n "$ROOM" ]    || { echo "--room обязателен (например: --room Розовая)"; exit 1; }

for f in bin/commandslistener.sh bin/alice_ipc_mon bin/tcpcat init/S97cmdlistener; do
  [ -s "$PAYLOAD/$f" ] || { echo "нет/пуст $PAYLOAD/$f — запустите save_to_payloads.sh"; exit 1; }
done

say()  { printf '\n\033[1;36m[lc]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[ERR]\033[0m %s\n' "$*" >&2; exit 1; }

if [ -z "$ALICEID" ]; then
  warn "ALICEID не задан."
  warn "Найти UUID через API УДЯ:"
  warn "  curl -s -H 'Authorization: Bearer <токен>' \\"
  warn "    https://api.iot.yandex.net/v1.0/user/info | jq \\"
  warn "    '.devices[] | select(.quasar_info.device_id==\"<серийник>\") | .id'"
  warn "Серийник (LY0000…) — это quasar_info.device_id, UUID — это поле \"id\"."
  die "перезапустите с --aliceid <UUID>"
fi

SSH="ssh -p $PORT -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
$SSH "root@$LITE_IP" "true" 2>/dev/null || die "SSH до $LITE_IP:$PORT не работает"

say "Читаю MAC wlan0 (SQP)"
SQP=$($SSH "root@$LITE_IP" "cat /sys/class/net/wlan0/address" | tr -d '\r\n ')
[ -n "$SQP" ] || die "не смог прочитать MAC wlan0"
echo "SQP=$SQP"

say "Параметры:"
cat <<EOF
  LITE_IP = $LITE_IP
  ROOM    = $ROOM
  SQP     = $SQP
  ALICEID = $ALICEID
  PLAYER  = ${PLAYER:-<не меняется: "Yandex Lite 2 SL">}
  LMS     = $LMS
  SAC     = http://$BRIDGE
EOF

say "sed локально: LMS, SAC, ROOM, SQP, ALICEID${PLAYER:+, PLAYER}"
TMP=$(mktemp)
SED_PLAYER=()
[ -n "$PLAYER" ] && SED_PLAYER=(-e "s#Yandex Lite 2 SL#$PLAYER#g")
sed \
  -e "s#^LMS=.*#LMS=$LMS#" \
  -e "s#^SAC=.*#SAC=http://$BRIDGE#" \
  -e "s#^ROOM=.*#ROOM='$ROOM'#" \
  -e "s#^SQP=.*#SQP='$SQP'#" \
  -e "s#^ALICEID=.*#ALICEID='$ALICEID'#" \
  "${SED_PLAYER[@]}" \
  "$PAYLOAD/bin/commandslistener.sh" > "$TMP"

sh -n "$TMP" || die "синтаксис commandslistener.sh сломан после sed"
echo "--- проверка результата ---"
grep -nE '^(LMS|SAC|ROOM|ALICEID|SQP)=' "$TMP"
if [ -n "$PLAYER" ]; then
  grep -F '"$RP" = "'"$PLAYER"'"' "$TMP" >/dev/null || die "announce(): имя плеера '$PLAYER' не подставилось"
fi

say "remount rw /"
$SSH "root@$LITE_IP" "mount -o remount,rw /"

say "Push бинарники → /data/opt/bin/"
cat "$PAYLOAD/bin/alice_ipc_mon" | $SSH "root@$LITE_IP" "cat > /data/opt/bin/alice_ipc_mon"
cat "$PAYLOAD/bin/tcpcat"        | $SSH "root@$LITE_IP" "cat > /data/opt/bin/tcpcat"
cat "$TMP"                       | $SSH "root@$LITE_IP" "cat > /data/opt/bin/commandslistener.sh"
$SSH "root@$LITE_IP" "chmod 755 /data/opt/bin/commandslistener.sh /data/opt/bin/alice_ipc_mon /data/opt/bin/tcpcat"
rm -f "$TMP"

say "Push S97cmdlistener → /etc/init.d/"
cat "$PAYLOAD/init/S97cmdlistener" | $SSH "root@$LITE_IP" "cat > /etc/init.d/S97cmdlistener && chmod 755 /etc/init.d/S97cmdlistener"

say "Проверка commandslistener.sh на колонке"
$SSH "root@$LITE_IP" "sh -n /data/opt/bin/commandslistener.sh && grep -nE '^(LMS|SAC|ROOM|ALICEID|SQP)=' /data/opt/bin/commandslistener.sh"

say "remount ro /"
$SSH "root@$LITE_IP" "sync; mount -o remount,ro /"

# --- Рестарт whatplays: busybox sh не отвязывает stdin/stdout от SSH-пайпа
# при "setsid ... &". Рабочий паттерн — двойной setsid + </dev/null + exit 0. ---
say "Рестарт commandslistener (двойной setsid, не блокируется)"

$SSH "root@$LITE_IP" '
  ps | grep -E "commandslis[t]ener\.sh|whatpl[a]ys\.sh" | awk "{print \$1}" | xargs -r kill -9 2>/dev/null || true
  ps | grep -E "while :; do /data/opt/bin/(whatplays|commandslistener)" | awk "{print \$1}" | xargs -r kill -9 2>/dev/null || true
  killall -9 alice_ipc_mon 2>/dev/null || true
  ps | grep -E "maind --cli" | awk "{print \$1}" | xargs -r kill -9 2>/dev/null || true
  sleep 3
  rm -f /tmp/whatplays.log /tmp/commandslistener.err /tmp/whatplays.last /tmp/wp.cancel.fifo
' || true

$SSH "root@$LITE_IP" 'setsid sh -c "setsid /bin/sh -c \"while :; do /data/opt/bin/commandslistener.sh >> /tmp/commandslistener.err 2>&1; sleep 3; done\"" </dev/null >/dev/null 2>&1 & sleep 1; exit 0' || true

sleep 5
$SSH "root@$LITE_IP" '
  echo "--- процессы ---"
  ps | grep -E "commandslis[t]ener|whatpl[a]ys|alice_ip[c]|tcpca[t]|main[d] --cli"
  echo "--- лог ---"
  [ -f /tmp/whatplays.log ] && tail -n 10 /tmp/whatplays.log || echo "лога нет"
  echo "--- err ---"
  [ -f /tmp/commandslistener.err ] && tail -n 5 /tmp/commandslistener.err || true
'

say "Смоук whats_playing_text"
RQ=$(printf '%s' "$ROOM" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g')
echo "GET http://$BRIDGE/cmd?action=whats_playing_text&room=$RQ"
$SSH "root@$LITE_IP" "busybox wget -q -O - 'http://$BRIDGE/cmd?action=whats_playing_text&room=$RQ'" | head -c 300
echo

cat <<EOF

============================================================
ГОТОВО. commandslistener (экс-whatplays v5) развёрнут.

ВАЖНО: если имя плеера (--name в squeezelite.sh) НЕ равно "Yandex Lite 2 SL",
перезапустите этот скрипт с --player "<то же имя>" — он сам поправит announce()
(ручной эквивалент ниже, если колонка уже развёрнута):

  ssh -p $PORT root@$LITE_IP '
    mount -o remount,rw /
    sed -i "s#Yandex Lite 2 SL#<ИМЯ_ПЛЕЕРА>#g" /data/opt/bin/commandslistener.sh
    sh -n /data/opt/bin/commandslistener.sh
    sync; mount -o remount,ro /
    ps | grep -E "whatpl[a]ys\\.sh" | awk "{print \\\$1}" | xargs -r kill -9 2>/dev/null || true
    sleep 2
    setsid sh -c "setsid /bin/sh -c \\"while :; do /data/opt/bin/commandslistener.sh >> /tmp/commandslistener.err 2>&1; sleep 3; done\\"" </dev/null >/dev/null 2>&1 &
  '

ПРОВЕРКА:
  ssh -p $PORT root@$LITE_IP
  tail -f /tmp/whatplays.log
  # сказать: «Алиса, дальше» → должно быть:
  #   ФРАЗА: альше · текст: Дальше
  #   → cancel (мгновенный)
  #   → next (трек/канал, room=$ROOM)

РЕЕСТР:
  http://192.168.1.131:8888/form
    строка: $ROOM;$LITE_IP;2222
    пароль root
  → Сохранить → «Проверить»
============================================================

EOF
