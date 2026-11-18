#!/bin/sh
# btkeys-grab.sh v2 — обвязка btrcv: строки "code value" -> /cmd sa_client + бип.
# Использование: btkeys-grab.sh <uniq-mac> <player> [room]
# <player> — ИМЯ ПЛЕЕРА LMS напрямую (без поиска по комнате).
SAC=http://192.168.1.131:8888
LMS=192.168.1.131:9000
UNIQ="${1:?uniq}"
PLAYER="${2:?player}"
ROOM="${3:-}"
LOG=/tmp/btkeys.log
BEEP=/data/opt/sounds/beep.wav
beep(){ [ -s "$BEEP" ] && aplay -q "$BEEP" >/dev/null 2>&1 & }
log(){ echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"; }
urlencode(){ printf '%s' "$1" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g'; }
lms(){ busybox wget -q -O - --header='Content-Type: application/json' --post-data="$1" "http://$LMS/jsonrpc.js" 2>/dev/null; }
cmd(){ busybox wget -q -O /dev/null "$SAC/cmd?action=$1&player=$(urlencode "$PLAYER")" 2>/dev/null; log "⇒ $1 ($PLAYER)"; }

log "=== btkeys-grab старт: player=$PLAYER room=$ROOM ==="
/data/opt/bin/btrcv "$UNIQ" 2>>/tmp/btrcv.err | while read -r code value; do
  [ "$value" = "1" ] || continue   # только нажатия
  beep
  case "$code" in
    105|165) cmd prev ;;                    # ◀ / KEY_PREVIOUS
    106|163) cmd next ;;                    # ▶ / KEY_NEXT
    353|164) cmd play_pause ;;              # OK (KEY_OK=353) / playpause=164
    115)     cmd volume_up ;;
    114)     cmd volume_dn ;;
    172)     [ -n "$ROOM" ] && { cmd switch_here "$ROOM"; log '(room mode)'; } ;;  # Home
    408|418|217) cmd whats_playing ;;       # Search — «что играет»
    2|3|4|5|6|7|8|9|10|11)                 # цифры 1-9,0 = избранное
      N=$((code - 1)); [ "$code" = "11" ] && N=10
      busybox wget -q -O /dev/null "$SAC/cmd?action=channel&player=$(urlencode "$PLAYER")&value=$N" 2>/dev/null
      log "⇒ избранное #$N ($PLAYER)" ;;
    *) log "код $code (не маплено)" ;;
  esac
done

