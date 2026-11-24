#!/bin/bash
# install_all.sh — ВСЕ фичи на рутованную Станцию Лайт ОДНОЙ командой (после root.sh!).
# Использование: ./install_all.sh <IP> [--room <Комната>] [--aliceid <UUID>]
#                [--name "<Имя squeezelite>"] [--player "<LMS-плеер пульта>"]
#                [--bridge 192.168.1.131:8888] [--lms 192.168.1.131:9000] [--airplay]
#
# Правила авто (минимум обязательного — «просто IP»):
#   - squeezelite: ставится всегда; имя по умолчанию "Yandex Lite 2 SL", LMS discovery;
#   - local_commands (голосовые фразы): нужен --room И --aliceid; без них этап
#     ПРОПУСКАЕТСЯ с подсказкой (UUID — manual.txt, раздел 15); доустановить позже;
#   - bt_remote (пульт): плеер — АВТО по правилу владельца: LMS-плеер в комнате
#     колонки (--room или из commandslistener станции, через status.json моста),
#     а если в комнате LMS-плеера нет → пульт рулит squeezelite самой станции;
#   - airplay: только с флагом --airplay (тяжёлый, Entware).
# Этапы идемпотентны — повторный запуск безопасен.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
cd "$HERE"

IP=""; ROOM=""; ALICEID=""; NAME=""; PLAYER=""; BRIDGE=""; LMS=""; AIRPLAY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --room)    ROOM="$2"; shift 2 ;;
    --aliceid) ALICEID="$2"; shift 2 ;;
    --name)    NAME="$2"; shift 2 ;;
    --player)  PLAYER="$2"; shift 2 ;;
    --bridge)  BRIDGE="$2"; shift 2 ;;
    --lms)     LMS="$2"; shift 2 ;;
    --airplay) AIRPLAY=1; shift ;;
    -*)        echo "неизвестный флаг $1"; exit 1 ;;
    *)         IP="$1"; shift ;;
  esac
done
[ -n "$IP" ] || { echo "использование: $0 <IP> [--room <Комната>] [--aliceid <UUID>] [--airplay]"; exit 1; }

say()  { printf '\n\033[1;36m[all]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m %s\n' "$*"; }

SQ_ARGS=(); [ -n "$NAME" ]   && SQ_ARGS+=(--name "$NAME")
LC_ARGS=(); [ -n "$NAME" ]   && LC_ARGS+=(--player "$NAME")
[ -n "$BRIDGE" ] && LC_ARGS+=(--bridge "$BRIDGE")
[ -n "$LMS" ]    && LC_ARGS+=(--lms "$LMS")
BT_ARGS=(); [ -n "$PLAYER" ] && BT_ARGS+=(--player "$PLAYER")
[ -n "$ROOM" ]   && BT_ARGS+=(--room "$ROOM")
[ -n "$BRIDGE" ] && BT_ARGS+=(--bridge "$BRIDGE")

say "1/4: squeezelite (LMS-плеер станции)"
./squeezelite.sh "$IP" "${SQ_ARGS[@]}"

say "2/4: local_commands (голосовые фразы)"
if [ -n "$ROOM" ] && [ -n "$ALICEID" ]; then
  ./local_commands.sh "$IP" --room "$ROOM" --aliceid "$ALICEID" "${LC_ARGS[@]}"
else
  warn "ПРОПУЩЕН: нужны --room и --aliceid (UUID колонки — manual.txt, раздел 15)."
  warn "Доустановить позже: ./local_commands.sh $IP --room <Комната> --aliceid <UUID>"
fi

say "3/4: bt_remote (Bluetooth-пульт; плеер — авто по комнате)"
./bt_remote.sh "$IP" "${BT_ARGS[@]}"

if [ "$AIRPLAY" = 1 ]; then
  say "4/4: airplay (по флагу --airplay)"
  ./airplay.sh "$IP"
else
  say "4/4: airplay пропущен (добавь --airplay, если нужен)"
fi

cat <<EOF

============================================================
ГОТОВО: фичи развёрнуты на $IP.

ДАЛЬШЕ (руками):
  1. Пульт: ssh -p 2222 root@$IP  →  /data/opt/bin/bt-connect.sh
     (зажать комбо паринга: G20S=OK+Return, ATV3=Back+Home, Xiaomi=Home+Menu)
  2. Реестр станций: http://192.168.1.131:8888/form → <Комната>;$IP;2222 + пароль
  3. Голосом: «Алиса, дальше», «Алиса, что играет»
  4. После ребута станции всё поднимется само (S97/S98/S99 init-скрипты)
============================================================

EOF
