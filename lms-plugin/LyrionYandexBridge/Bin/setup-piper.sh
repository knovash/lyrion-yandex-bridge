#!/bin/sh
# ============================================================================
# setup-piper.sh — установка локального TTS Piper для Lyrion Yandex Bridge.
# Запускается кнопкой со страницы плагина LMS (от пользователя LMS, БЕЗ root).
# Кладёт бинарник+голос в <datadir>/piper/, прописывает пути в
# data/settings_voice.properties, самопроверяется синтезом. Повторяемо и
# ничего не удаляет. Существующие пути (например /opt/piper) не трогает:
# клиенту всё равно, откуда запускать — берётся то, что в настройках.
#
# Использование: sh setup-piper.sh <datadir>
# Коды выхода: 0 ok, 1 ошибка (текст в stdout — показывается в логе плагина)
# ============================================================================
set -u
DIR="${1:?нет параметра: datadir}"
DEST="$DIR/piper"
LOG="$DIR/piper-install.log"
: > "$LOG" 2>/dev/null || { echo "нет прав на запись в $DIR"; exit 1; }
exec >> "$LOG" 2>&1

say(){ echo "[$(date '+%H:%M:%S')] $*"; }

PIPER_REL="https://github.com/rhasspy/piper/releases/download/2023.11.14-2"
VOICE_BASE="https://huggingface.co/rhasspy/piper-voices/resolve/main/ru/ru_RU/irina/medium"

dl(){ # dl <url> <файл> — curl или wget, что есть
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --connect-timeout 20 -o "$2" "$1"
  elif command -v wget >/dev/null 2>&1; then
    wget -q --tries=3 --timeout=30 -O "$2" "$1"
  else
    say "НЕТ curl/wget — установи: sudo apt install curl"; return 1
  fi
}

say "=== установка Piper в $DEST ==="
mkdir -p "$DEST" || { say "не создал $DEST"; exit 1; }

# --- 1. архитектура ---
case "$(uname -m)" in
  x86_64)         PARCH="x86_64" ;;
  aarch64|arm64)  PARCH="aarch64" ;;
  armv7l|armv6l)  PARCH="armv7l" ;;
  *) say "неподдерживаемая архитектура: $(uname -m)"; exit 1 ;;
esac
say "архитектура: $PARCH"

# --- 2. бинарник (если ещё нет) ---
if [ -x "$DEST/piper/piper" ]; then
  say "бинарник уже есть: $DEST/piper/piper"
else
  say "качаю piper ($PARCH, ~5МБ) с github.com/rhasspy/piper ..."
  dl "$PIPER_REL/piper_linux_$PARCH.tar.gz" "$DEST/piper.tgz" \
    || { say "СКАЧИВАНИЕ НЕ УДАЛОСЬ (нет интернета до github?). Положи руками:"; 
         say "  $PIPER_REL/piper_linux_$PARCH.tar.gz -> $DEST/piper.tgz и запусти снова"; exit 1; }
  tar xzf "$DEST/piper.tgz" -C "$DEST" && rm -f "$DEST/piper.tgz"
  [ -x "$DEST/piper/piper" ] || { say "в архиве нет piper/piper"; exit 1; }
  say "бинарник установлен: $DEST/piper/piper ($(stat -c%s "$DEST/piper/piper" 2>/dev/null) б)"
fi

# --- 3. голос irina-medium (если ещё нет) ---
VOICE="$DEST/ru_RU-irina-medium.onnx"
if [ -s "$VOICE" ]; then
  say "голос уже есть: $VOICE"
else
  say "качаю голос irina-medium (~63МБ) с huggingface.co ..."
  dl "$VOICE_BASE/ru_RU-irina-medium.onnx"     "$VOICE"        || { say "голос .onnx не скачался (huggingface недоступен?). Положи руками: $VOICE"; exit 1; }
  dl "$VOICE_BASE/ru_RU-irina-medium.onnx.json" "$VOICE.json"  || say "WARN: .onnx.json не скачался (не критично)"
  say "голос установлен ($(stat -c%s "$VOICE") б)"
fi

# --- 4. самопроверка синтеза ---
say "самопроверка: синтез «тест»..."
printf 'тест' | "$DEST/piper/piper" --model "$VOICE" --output_file "$DEST/selftest.wav" >/dev/null 2>&1
if [ -s "$DEST/selftest.wav" ]; then
  say "синтез ОК ($(stat -c%s "$DEST/selftest.wav") б)"; rm -f "$DEST/selftest.wav"
else
  say "СИНТЕЗ НЕ РАБОТАЕТ — посмотри $LOG"; exit 1
fi

# --- 5. прописать пути в settings_voice.properties ---
PROP="$DIR/data/settings_voice.properties"
mkdir -p "$DIR/data"
touch "$PROP"
for k in VOICE_STRING_PIPER_BIN VOICE_STRING_PIPER_VOICE; do
  grep -v "^$k=" "$PROP" > "$PROP.tmp" 2>/dev/null; mv "$PROP.tmp" "$PROP"
done
echo "VOICE_STRING_PIPER_BIN=$DEST/piper/piper"  >> "$PROP"
echo "VOICE_STRING_PIPER_VOICE=$VOICE"           >> "$PROP"
say "настройки записаны: $PROP"

touch "$DEST/INSTALLED"
say "=== ГОТОВО. Перезапусти клиента (кнопка Restart на странице плагина) ==="
exit 0
