#!/bin/sh
# btkeys-grab.sh v3 — обвязка btrcv: строки "code value" -> /cmd sa_client + бип.
# Использование: btkeys-grab.sh <uniq-mac> <player> [room]
#   <player> — ИМЯ ПЛЕЕРА LMS напрямую (switch_here идёт по плееру; room — в журнал).
# Сервер bridge: строка "server <ip[:port]>" в /data/opt/bt.conf, дефолт — ниже.
# v3: адрес из bt.conf (переезд сервера = правка одной строки, без правки скриптов);
#     ошибки /cmd видны в логе («✗ … подряд: N») + двойной бип после 3 подряд;
#     бип не плодит параллельные aplay; трим лога при старте (RAM станции мала);
#     heartbeat /tmp/bt.status.<uniq>; убраны мёртвый lms() и ложный «room mode».
CONF=/data/opt/bt.conf
SAC=http://192.168.1.131:8888
SRV=$(grep -E '^server ' "$CONF" 2>/dev/null | head -1 | awk '{print $2}')
case "$SRV" in
  http://*) SAC="$SRV" ;;
  ?*)       SAC="http://$SRV" ;;
esac
UNIQ="${1:?uniq}"
PLAYER="${2:?player}"
ROOM="${3:-}"
LOG=/tmp/btkeys.log
BEEP=/data/opt/sounds/beep.wav
FAILS=/tmp/btkeys.fails
ST="/tmp/bt.status.$(echo "$UNIQ" | tr ':' '.')"
urlencode(){ printf '%s' "$1" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g'; }
log(){ echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"; }
# бип: не чаще одного aplay (серия нажатий не плодит процессы на слабой станции)
beep(){ [ -s "$BEEP" ] && { pidof aplay >/dev/null 2>&1 || aplay -q "$BEEP" >/dev/null 2>&1 & }; }
# heartbeat: <ts> <uniq> <pid btrcv> — для диагностики/страницы /stations
hb(){ echo "$(date +%s) $UNIQ $(pidof btrcv 2>/dev/null | cut -d' ' -f1)" > "$ST"; }
# трим лога при старте
[ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 65536 ] && \
  { tail -c 32768 "$LOG" > "$LOG.t" 2>/dev/null && mv "$LOG.t" "$LOG"; }
rm -f "$FAILS"

cmd(){
  # $1 = action; $2 = доп. параметры URL (например "&value=7")
  if busybox wget -q -O /dev/null -T 4 "$SAC/cmd?action=$1&player=$(urlencode "$PLAYER")$2" 2>/dev/null; then
    rm -f "$FAILS"
    log "⇒ $1 ($PLAYER)"
  else
    n=0; [ -f "$FAILS" ] && n=$(cat "$FAILS" 2>/dev/null)
    case "$n" in ''|*[!0-9]*) n=0 ;; esac
    n=$((n+1)); echo "$n" > "$FAILS"
    log "✗ $1 ($PLAYER) — bridge недоступен ($SAC), подряд: $n"
    [ "$n" = 3 ] && { beep; sleep 0.3; beep; }   # сигнал: сервер молчит
  fi
}

log "=== btkeys-grab v3 старт: player=$PLAYER room=$ROOM sac=$SAC ==="
hb
/data/opt/bin/btrcv "$UNIQ" 2>>/tmp/btrcv.err | while read -r code value; do
  [ "$value" = "1" ] || continue   # только нажатия
  beep; hb
  case "$code" in
    105|165) cmd prev ;;                    # ◀ / KEY_PREVIOUS
    106|163) cmd next ;;                    # ▶ / KEY_NEXT
    353|164) cmd play_pause ;;              # OK (KEY_OK=353) / playpause=164
    115)     cmd volume_up ;;
    114)     cmd volume_dn ;;
    172)     cmd switch_here ;;             # Home — комната берётся из плеера
    408|418|217) cmd whats_playing ;;       # Search — «что играет»
    2|3|4|5|6|7|8|9|10|11)                 # цифры 1-9,0 = избранное
      N=$((code - 1))
      cmd channel "&value=$N" ;;
    *) log "код $code (не маплено)" ;;
  esac
done
log "=== btkeys-grab: поток btrcv завершён, супервизор перезапустит ==="
