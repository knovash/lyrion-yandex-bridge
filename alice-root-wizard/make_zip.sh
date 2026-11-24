#!/bin/bash
# make_zip.sh (v3) — собрать МАЛЕНЬКИЙ alice-root-wizard.zip (~4 МБ) в корень sa_client.
#
# РЕШЕНИЕ ВЛАДЕЛЬЦА (24.11, РАЗДЕЛЕНИЕ): большой файл прошивки (.46, 64 МБ)
# хранится в git ОТДЕЛЬНО (lite-kit/firmware/yandexmicro/<версия>.zip, свой
# стабильный raw-URL); в zip входят ТОЛЬКО скрипты + payload_* + lite-kit
# БЕЗ прошивки. Оба файла обновляются обычным пушем, НЕ привязаны к релизам.
#
# На чистой машине: скачать прошивку raw-URL'ом (stand.sh печатает ссылку)
# или ./lite-kit/install-firmware.sh -p yandexmicro -v <версия> (зеркало).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
OUT="$ROOT/alice-root-wizard.zip"
cd "$HERE"
rm -f "$OUT"
zip -rq "$OUT" . -x 'lite-kit/run/*' 'lite-kit/log/*' 'lite-kit/firmware/yandexmicro/*.zip' 'lite-kit/firmware/*.zip'
echo "ГОТОВО (БЕЗ прошивки — она отдельный файл в git): $OUT"
ls -la "$OUT"
sha256sum "$OUT"
echo "Состав: $(unzip -l "$OUT" | tail -1)"
echo "Прошивка (отдельный файл): lite-kit/firmware/yandexmicro/ — raw-URL см. manual.txt"
