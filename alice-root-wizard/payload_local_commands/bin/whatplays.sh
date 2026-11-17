#!/bin/sh
# whatplays v5: арбитр звука + перехват фразы «дальше» → next_channel (sa_client).
# Арбитраж: S 1/2 (Алиса слушает) → пауза LMS-музыки; S 0 → музыка обратно.
# Фраза «дальше» (шаблон «альше», без первой буквы — регистронезависимо)
# → /cmd?action=next_channel&player=Yandex+Lite+SL — то же, что «переключи канал».
LOG=/tmp/whatplays.log
SPEAK=/tmp/wp.speaking
COOLDOWN=4
SQP='b8:87:6e:2b:d4:b4'
LMS=192.168.1.137:9000
SAC=http://192.168.1.131:8888
ROOM='Гостиная'
ALICEID='0c1ae602-f240-4f0d-ab20-f0cb52cd05ec'
PATTERNS='альше
азад
овтор
андом
тдельно
месте
ереключи
грает'   # быстрые СЛОВА (однословные — for по пробелам!): ловим на P и T
SPOPAT='потифай
otify
збранное'      # спотифай/избранное: только финальный (T) — нужен хвост фразы

for d in /proc/[0-9]*/cmdline; do
  case "$(tr '\0' ' ' < "$d" 2>/dev/null)" in
    *whatplays.sh*|*maind\ --cli*|*alice_ipc_mon*)
      q=${d#/proc/}; q=${q%/cmdline}
      [ "$q" != "$$" ] && [ "$q" != "$PPID" ] && kill "$q" 2>/dev/null ;;
  esac
done
log(){ echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"; }

lms(){ busybox wget -q -O - --header='Content-Type: application/json' --post-data="$1" "http://$LMS/jsonrpc.js" 2>/dev/null; }
jstat(){ lms "{\"id\":1,\"method\":\"slim.request\",\"params\":[\"$1\",[\"status\",\"-\",\"1\",\"tags:aA\"]]}" ; }
mode(){ printf '%s' "$1" | tr ',' '\n' | sed -n 's/.*"mode":"\([^"]*\)".*/\1/p' | head -1; }
field(){ printf '%s' "$1" | tr ',' '\n' | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" | head -1; }
numfield(){ printf '%s' "$1" | tr ',' '\n' | sed -n "s/.*\"$2\":\([0-9][0-9]*\).*/\1/p" | head -1; }
sq_engaged(){ Q=$(pidof squeezelite); [ -n "$Q" ] && [ "$(ls -la /proc/$Q/fd 2>/dev/null | grep -c pcmC)" -gt 0 ]; }
pause_music(){ lms "{\"id\":1,\"method\":\"slim.request\",\"params\":[\"$SQP\",[\"pause\",\"1\"]]}" >/dev/null; PAUSED=1; log 'пауза'; }
resume_music(){ lms "{\"id\":1,\"method\":\"slim.request\",\"params\":[\"$SQP\",[\"play\",\"\"]]}" >/dev/null; PAUSED=0; log 'продолжение'; }
urlencode(){ printf '%s' "$1" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g'; }

# --- постоянная CLI-сессия maind для мгновенного cancel диалога ---
FIFO=/tmp/wp.cancel.fifo
cancel_start(){
  rm -f "$FIFO"; mkfifo "$FIFO"
  Q=/system/vendor/quasar/libs
  # ОДНА копия maind --cli (читатель fifo), затем exec 3>. БЕЗ сторожа:
  # перезапуск по таймеру разводил десятки копий и душил колонку (load 87).
  LD_LIBRARY_PATH=$Q /system/vendor/quasar/maind --cli < "$FIFO" > /dev/null 2>&1 &
  exec 3>"$FIFO"
  sleep 2
  log "CLI-сессия cancel: одиночная (без сторожа)"
}
# запасной путь: если fd3 умер — короткоживущий maind на одно действие
cli_fallback(){ TXT="$1"
  ( sleep 1.2; printf '%s' "$TXT"; sleep 1 ) | \
    LD_LIBRARY_PATH=/system/vendor/quasar/libs /system/vendor/quasar/maind --cli >/dev/null 2>&1 &
}
cancel_say(){ printf 'c\n' >&3 2>/dev/null && log '→ cancel (мгновенный)' || { cli_fallback 'c\n'; log '→ cancel (фолбэк)'; } }
speak(){ ENC=$(urlencode "$1")
  busybox wget -q -O /dev/null "$SAC$ENC" 2>/dev/null
  log "speak: $1"
}

track_now(){ # титул SL-плеера (в т.ч. на паузе от арбитража), иначе любой играющий
  J=$(jstat "$SQP"); M=$(mode "$J")
  if [ "$M" = play ] || [ "$M" = pause ]; then
    A=$(field "$J" artist); T=$(field "$J" title); [ -n "$T" ] || T=$(field "$J" current_title)
    if [ -n "$T" ]; then [ -n "$A" ] && T="$A — $T"; echo "$T"; return; fi
  fi
  PL=$(lms '{"id":1,"method":"slim.request","params":["",["players","0","20"]]}')
  for P in $(printf '%s' "$PL" | tr ',' '\n' | grep -o '"playerid":"[^"]*"' | cut -d'"' -f4); do
    J=$(jstat "$P"); [ "$(mode "$J")" = play ] || continue
    A=$(field "$J" artist); T=$(field "$J" title)
    if [ -n "$T" ]; then [ -n "$A" ] && T="$A — $T"; echo "$T"; return; fi
  done
}

hook_spotify(){
  log "СПОтифай: '$1'"
  cancel_say
  RE=$(urlencode "$ROOM")
  Q="$1"
  ACT=spotify_artist; CMDPREF='включи'
  case "$Q" in
    *альбом*) ACT=spotify_album; CMDPREF='включи альбом'; Q=$(printf '%s' "$Q" | sed 's/[а-я]*альбом//; s/^ *//; s/ *$//') ;;
    *трек*) ACT=spotify_track; CMDPREF='включи трек'; Q=$(printf '%s' "$Q" | sed 's/[а-я]*трек//; s/^ *//; s/ *$//') ;;
    *плейлист*) ACT=spotify_playlist; CMDPREF='включи плейлист'; Q=$(printf '%s' "$Q" | sed 's/[а-я]*плейлист//; s/^ *//; s/ *$//') ;;
  esac
  [ -n "$Q" ] || { log 'пустой запрос — пропуск'; return; }
  AE=$(urlencode "$Q")
  ANS=$(busybox wget -q -O - "$SAC/cmd?action=$ACT&room=$RE&value=$AE" 2>/dev/null | head -c 120)
  log "→ $ACT ($CMDPREF '$Q') ответ: $ANS"
  case "$ANS" in включаю*|Включаю*|не\ нашла*) announce "$ANS" ;; esac
}

# нативная озвучка: только если целевой плеер НЕ собственный сквизлайт Лайта
# (случай 1: сам играет — молчим; случай 2: играет другой — Алиса говорит)
# озвучка: голосом Алисы (облачный сценарий УДЯ, настройка в sa_client) или
# локальным Пайпером — что включено тумблером на сервере. Ответ:
#   "ALICE ok" — сказала колонка; "PIPER <url>" — скачать и сыграть aplay'ем.
announce(){
  [ -n "$1" ] || return
  RP=$(room_player)
  [ "$RP" = "Yandex Lite 2 SL" ] && return
  VE=$(urlencode "$1")
  ANS=$(busybox wget -q -O - "$SAC/cmd?action=voice_say&alice=$ALICEID&value=$VE" 2>/dev/null | head -c 250)
  case "$ANS" in
    ALICE*)
      log "⇒ Алиса (облако): $1"
      ;;
    PIPER\ *)
      busybox wget -q -O /tmp/wp.voice.wav "${ANS#PIPER }" 2>/dev/null
      if [ -s /tmp/wp.voice.wav ]; then
        aplay -q /tmp/wp.voice.wav 2>/dev/null && log "⇒ Piper (локально): $1"
        rm -f /tmp/wp.voice.wav
      fi
      ;;
    *)
      log "⇒ озвучка не удалась: $ANS"
      ;;
  esac
}

hook_favorite(){
  log "ИзБранное: '$1'"
  cancel_say
  RE=$(urlencode "$ROOM"); AE=$(urlencode "$1")
  ANS=$(busybox wget -q -O - "$SAC/cmd?action=favorite_name&room=$RE&value=$AE" 2>/dev/null | head -c 120)
  log "→ favorite_name ответ: $ANS"
}

# имя LMS-плеера, привязанного к комнате ROOM (через status.json клиента)
room_player(){
  busybox wget -q -O - "$SAC/status.json" 2>/dev/null | sed 's/},/}\n/g' \
    | grep "\"room\":\"$ROOM\"" | sed -n 's/.*"name":"\([^"]*\)".*/\1/p' | head -1
}

hook(){
  PT=$1; PH=${2:-}
  log "ФРАЗА: $PT · текст: $PH"
  # «ереключи…»: диалог отменяем ТОЛЬКО когда фраза дозрела (в ветках ниже).
  # Мгновенный cancel на частичной «переключи (музыку)» обрывал прослушивание —
  # Алиса не дослушивала название комнаты.
  [ "$PT" = ереключи ] || cancel_say
  RE=$(urlencode "$ROOM")
  case "$PT" in
    альше)
      busybox wget -q -O /dev/null "$SAC/cmd?action=next&room=$RE" 2>/dev/null
      log "→ next (трек/канал, room=$ROOM)"
      ;;
    азад)
      busybox wget -q -O /dev/null "$SAC/cmd?action=prev&room=$RE" 2>/dev/null
      log "→ prev (трек/канал, room=$ROOM)"
      ;;
    тдельно)
      busybox wget -q -O /dev/null "$SAC/cmd?action=separate_on&room=$RE" 2>/dev/null
      log "→ separate_on (Отдельно, room=$ROOM)"
      ;;
    месте)
      busybox wget -q -O /dev/null "$SAC/cmd?action=separate_off&room=$RE" 2>/dev/null
      log "→ separate_off (Вместе, room=$ROOM)"
      ;;
    ереключи)
      case "$PH" in
        *ереключи\ сюда*)
          cancel_say
          busybox wget -q -O /dev/null "$SAC/cmd?action=switch_here&room=$RE" 2>/dev/null
          log "→ switch_here (room=$ROOM)"
          ;;
        *ереключи\ музыку*)
          # «переключи музыку в/на <комната|плеер>» — хвост после предлога → switch_room
          # (sa_client нечётко матчит комнату в любом падеже или плеер → его комната)
          TAIL=$(printf '%s' "$PH" | sed -e 's/.*ереключи музыку//' -e 's/^[[:space:]]*//' -e 's/^в[[:space:]][[:space:]]*//' -e 's/^на[[:space:]][[:space:]]*//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
          case "$TAIL" in ?|??|в|во|на) TAIL='' ;; esac  # короткий хвост — фраза ещё не дополнена
          if [ -n "$TAIL" ]; then
            cancel_say
            TE=$(urlencode "$TAIL")
            ANS=$(busybox wget -q -O - "$SAC/cmd?action=switch_room&room=$RE&value=$TE" 2>/dev/null | head -c 120)
            log "→ switch_room ('$TAIL') ответ: $ANS"
            case "$ANS" in нет\ такой*|в\ комнате*) announce "$ANS" ;; esac
          else
            echo '0|музыку' > /tmp/whatplays.last   # сброс кулдауна: ждём хвост фразы
            log 'переключи музыку: хвост пуст — ждём продолжение'
          fi
          ;;
        *)
          echo '0|ереключи' > /tmp/whatplays.last   # сброс кулдауна: ждём «сюда»/«музыку X»
          log 'переключи без «сюда»/«музыку» — пропущено'
          ;;
      esac
      ;;
    грает)
      # «что играет» — голосом колонки (та же логика, что навык «Раз Два»;
      # озвучка — как у «спотифай»: voice_say Алиса / Piper wav + aplay)
      case "$PH" in
        *то*играет*|*чё*играет*|*чо*играет*)
          ANS=$(busybox wget -q -O - "$SAC/cmd?action=whats_playing_text&room=$RE" 2>/dev/null | head -c 300)
          log "→ что играет: $ANS"
          announce "$ANS"
          ;;
        *)
          echo '0|грает' > /tmp/whatplays.last    # сброс кулдауна: ждём «что играет»
          log '«играет» без «что» — пропущено'
          ;;
      esac
      ;;
    андом)
      # тумблер рандома на плеере комнаты: 0 ↔ 1
      RP=$(room_player)
      [ -n "$RP" ] || { log 'не нашёл плеер комнаты'; return; }
      S=$(numfield "$(jstat "$RP")" "playlist shuffle")
      case "$S" in ''|*[!0-9]*) S=0 ;; esac
      if [ "$S" = 0 ]; then ACT=shuffle_on; NEW=1; else ACT=shuffle_off; NEW=0; fi
      busybox wget -q -O /dev/null "$SAC/cmd?action=$ACT&player=$RP" 2>/dev/null
      log "→ рандом: $RP ($S → $NEW)"
      ;;
    овтор)
      # тумблер повтора на плеере комнаты: 0 (выкл) ↔ 2 (повтор всех)
      RP=$(room_player)
      [ -n "$RP" ] || { log 'не нашёл плеер комнаты'; return; }
      R=$(numfield "$(jstat "$RP")" "playlist repeat")
      case "$R" in ''|*[!0-9]*) R=0 ;; esac
      if [ "$R" = 0 ]; then ACT=repeat_on; NEW=2; else ACT=repeat_off; NEW=0; fi
      busybox wget -q -O /dev/null "$SAC/cmd?action=$ACT&player=$RP" 2>/dev/null
      log "→ повтор: $RP ($R → $NEW)"
      ;;
  esac
}

PAUSED=0
mkdir -p /tmp
case "${1:-}" in
  test) sq_engaged && echo ЗАНЯТО || echo СВОБОДНО; exit 0 ;;
  say)  shift; speak "$1"; exit 0 ;;
  what) track_now; exit 0 ;;
esac
log '=== старт v5 (арбитр + фразы: дальше/повтор, room-логика) ==='
cancel_start
while :; do
  /data/opt/bin/alice_ipc_mon | while read -r L; do
    set -- $L
    if [ "$1" = "S" ]; then
      case "$2" in
        1|2) sq_engaged && pause_music ;;
        0)   if [ "$PAUSED" = 1 ] && [ ! -f "$SPEAK" ]; then sleep 1; resume_music; fi ;;
      esac
    elif [ "$1" = "T" ] || [ "$1" = "P" ]; then
      KIND=$1; shift; PHRASE="$*"
      # --- спотифай / избранное: только на финальном тексте (хвост фразы) ---
      if [ "$KIND" = "T" ]; then
        case "$PHRASE" in
          *збранное*)
            TAIL=$(printf '%s' "$PHRASE" | sed -e 's/.*[иИ]збранное//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
            if [ -n "$TAIL" ]; then
              NOW=$(date +%s); echo "$NOW|fav" > /tmp/whatplays.last
              hook_favorite "$TAIL"
            fi
            ;;
          *потифай*|*[sS]potify*)
            ART=$(printf '%s' "$PHRASE" | sed -e 's/.*[сС]потифай//' -e 's/.*[sS]potify//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
            if [ -n "$ART" ]; then
              NOW=$(date +%s); echo "$NOW|spotify" > /tmp/whatplays.last
              hook_spotify "$ART"
            fi
            ;;
        esac
      fi
      # --- быстрые слова (дальше/повтор): P и T ---
      for PT in $PATTERNS; do
        case "$PHRASE" in *$PT*)
          NOW=$(date +%s); LAST=0
          [ -f /tmp/whatplays.last ] && LAST=$(cut -d'|' -f1 /tmp/whatplays.last 2>/dev/null)
          case "$LAST" in ''|*[!0-9]*) LAST=0 ;; esac
          [ $(( NOW - LAST )) -ge "$COOLDOWN" ] || continue
          echo "$NOW|$PT" > /tmp/whatplays.last
          hook "$PT" "$PHRASE"
          break
        esac
      done
    fi
  done
  log 'IPC потерян, переподключение'
  sleep 2
done
