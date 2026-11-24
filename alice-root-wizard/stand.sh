#!/bin/bash
# stand.sh (v2) — стенд lite-kit для рута Лайт 1 (yandexmicro).
# v2: lite-kit УЖЕ В АРХИВЕ (alice-root-wizard/lite-kit/, с прошивкой .46) —
#     интернет для клона НЕ нужен; git clone остался фолбэком для dev-машин.
# Освобождает :53, :80, :443; прошивка ищется локально в первую очередь.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
LITE_KIT_DIR="${LITE_KIT_DIR:-$HERE/lite-kit}"
FW_VERSION="0.369.1.46.4778262442.20261013.297"
FW_NAME="$FW_VERSION.zip"
FW_DIR="$LITE_KIT_DIR/firmware/yandexmicro"
FW_ZIP="$FW_DIR/$FW_NAME"

say()  { printf '\n\033[1;36m[stand]\033[0m %s\n' "$*"; }
warn() { printf '\n\033[1;33m[WARN]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[ERR]\033[0m %s\n' "$*" >&2; exit 1; }

say "Зависимости..."
sudo apt update
sudo apt install -y openssl git dnsutils android-tools-adb || \
  sudo apt install -y openssl git dnsutils adb

if ! command -v dnsmasq >/dev/null 2>&1; then
  say "Установка dnsmasq (сервис будет остановлен)..."
  sudo DEBIAN_FRONTEND=noninteractive apt install -y dnsmasq || true
fi

# --- Освободить :53 ---
say "Останавливаю системный dnsmasq (lite-kit поднимет свой)"
sudo systemctl stop dnsmasq 2>/dev/null || true
sudo systemctl disable dnsmasq 2>/dev/null || true
sudo pkill -9 dnsmasq 2>/dev/null || true
sleep 1

if sudo ss -tulpn 2>/dev/null | grep -q '0\.0\.0\.0:53 '; then
  warn "0.0.0.0:53 занят — останавливаю systemd-resolved"
  sudo systemctl stop systemd-resolved 2>/dev/null || true
  sleep 1
fi

# --- Освободить :80 и :443 (apache2 + старые python lite-kit) ---
say "Освобождаю :80 и :443"
sudo systemctl stop apache2 2>/dev/null || true
sudo systemctl disable apache2 2>/dev/null || true
sudo pkill -9 apache2 2>/dev/null || true

for pid in $(sudo ss -tulpn 2>/dev/null | awk '/:(80|443) /{if (match($0,/pid=[0-9]+/)) print substr($0,RSTART+4,RLENGTH-4)}' | sort -u); do
  cmd=$(sudo ps -p "$pid" -o cmd= 2>/dev/null || true)
  case "$cmd" in
    *python3*|*lite-kit*|*start.sh*)
      say "  kill $pid ($cmd)"
      sudo kill -9 "$pid" 2>/dev/null || true
      ;;
  esac
done
sleep 2

for port in 80 443; do
  if sudo ss -tulpn 2>/dev/null | grep -q ":$port "; then
    warn "порт :$port всё ещё занят:"
    sudo ss -tulpn | grep ":$port "
  else
    say "  :$port свободен ✓"
  fi
done

# --- lite-kit: ЛОКАЛЬНЫЙ из архива (фолбэк — clone) ---
if [ -f "$LITE_KIT_DIR/start.sh" ]; then
  say "lite-kit: $LITE_KIT_DIR (локальный, из архива — клон не нужен)"
else
  warn "lite-kit нет в $LITE_KIT_DIR — git clone (фолбэк, нужен интернет)"
  git clone https://github.com/krevetkqqq/lite-kit "$LITE_KIT_DIR"
fi
cd "$LITE_KIT_DIR"
chmod +x ./*.sh 2>/dev/null || true

# --- Прошивка .46 (уже в архиве; фолбэки — dev-пути) ---
say "Проверка прошивки $FW_NAME"
mkdir -p "$FW_DIR"
if [ -f "$FW_ZIP" ]; then
  say "Прошивка на месте: $FW_ZIP"
else
  FOUND=""
  for cand in \
      "$HOME/tools/lite-kit/firmware/yandexmicro/$FW_NAME" \
      "$HOME/lite-kit/firmware/yandexmicro/$FW_NAME" \
      "$HOME/lite-backup/$FW_NAME" \
      "$HOME/$FW_NAME"; do
    [ -f "$cand" ] && { FOUND="$cand"; break; }
  done
  if [ -n "$FOUND" ]; then
    say "Нашёл $FOUND — копирую"
    cp -v "$FOUND" "$FW_ZIP"
  else
    warn "Прошивка НЕ найдена: $FW_ZIP"
    die "нет $FW_NAME — положите zip в lite-kit/firmware/yandexmicro/"
  fi
fi

if [ ! -f "$FW_DIR/manifest.json" ]; then
  for cand in \
      "$HOME/tools/lite-kit/firmware/yandexmicro/manifest.json" \
      "$HOME/lite-kit/firmware/yandexmicro/manifest.json" \
      "$HOME/lite-backup/manifest.json"; do
    [ -f "$cand" ] && { cp -v "$cand" "$FW_DIR/manifest.json"; break; }
  done
fi

# --- IP и инструкции ---
IP=$(ip route get 1.1.1.1 2>/dev/null | awk '/src/{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1)
[ -n "$IP" ] || IP=$(hostname -I | awk '{print $1}')
say "IP ноутбука: $IP"

cat <<EOF

============================================================
СЛЕДУЮЩИЕ ШАГИ (руками, пока стенд работает):

1) Роутер: DNS 1 = $IP , DNS 2 = пусто (или тот же $IP).

2) Сброс колонки: питание → при подсветке зажать ▶‖ 6-10с
   до жёлто-зелёного кольца.

3) «Дом с Алисой» (телефон на LTE!): дойти до экрана
   пароля Wi-Fi и ОСТАНОВИТЬСЯ. Пароль не вводить.

4) В другом терминале:  ./root.sh <IP_колонки>
   IP колонки — в логе стенда ниже.

Стенд в форграунде, Ctrl+C — остановить.
============================================================

EOF

say "Запуск sudo ./start.sh..."
exec sudo ./start.sh
