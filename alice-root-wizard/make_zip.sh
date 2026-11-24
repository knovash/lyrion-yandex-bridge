#!/bin/bash
# make_zip.sh — собрать САМОДОСТАТОЧНЫЙ alice-root-wizard.zip в корень sa_client.
# Внутри: скрипты этапов, payload_* (бинарники+конфиги колонок) и lite-kit/
# с прошивкой .46 (~67МБ) — архив рутует колонку и ставит фичи с нуля,
# без git clone lite-kit (Entware для BT-этапа качается на станции через yse).
#
# ВНИМАНИЕ: zip ~70МБ. Пушить его в git (публичный репо, raw-URL со страницы
# /stations) — только по явному решению владельца: прошивка Яндекса + вес.
# Пока не решено — zip лежит локально и НЕ добавляется в коммит.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
OUT="$ROOT/alice-root-wizard.zip"

FW=$(ls "$HERE"/lite-kit/firmware/yandexmicro/*.zip 2>/dev/null | head -1)
[ -n "$FW" ] || { echo "[ERR] нет lite-kit/firmware/yandexmicro/*.zip — см. manual.txt"; exit 1; }

cd "$HERE"
rm -f "$OUT"
zip -rq "$OUT" . -x 'lite-kit/run/*' 'lite-kit/log/*'

echo "ГОТОВО: $OUT"
ls -la "$OUT"
sha256sum "$OUT"
echo "Состав: $(unzip -l "$OUT" | tail -1)"
