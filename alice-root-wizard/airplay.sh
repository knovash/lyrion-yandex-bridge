#!/bin/bash
# airplay.sh — ставит AirPlay 2 (shairport-sync + nqptp + avahi) на рутованную колонку.
# Использование: ./airplay.sh <IP>
# Требует: root, payload_airplay (если save_to_payloads.sh собирал с колонки с AirPlay).
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PAYLOAD="$HERE/payload_airplay"
PORT=2222

LITE_IP="${1:-}"
[ -n "$LITE_IP" ] || { echo "использование: $0 <IP>"; exit 1; }

say()  { printf '\n\033[1;36m[airplay]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[ERR]\033[0m %s\n' "$*" >&2; exit 1; }

SSH="ssh -p $PORT -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
$SSH "root@$LITE_IP" "true" 2>/dev/null || die "SSH до $LITE_IP:$PORT не работает"

HAS_PAYLOAD=0
if [ -f "$PAYLOAD/init/S99zairplay-lite1" ] || [ -d "$PAYLOAD/opt/airplay" ]; then
  HAS_PAYLOAD=1
fi

if [ "$HAS_PAYLOAD" = 0 ]; then
  warn "payload_airplay пуст — делаю чистую установку из Entware"
  warn "  AIRPLAY_IP=<ip колонки с AirPlay> ./save_to_payloads.sh"
fi

say "remount rw /"
$SSH "root@$LITE_IP" "mount -o remount,rw /"

say "Проверка Entware"
HAS_ENTWARE=$($SSH "root@$LITE_IP" "test -x /opt/bin/opkg && echo yes || echo no" | tr -d '\r')
if [ "$HAS_ENTWARE" != "yes" ]; then
  warn "Entware не установлен. Пробую через /data/opt/bin/yse..."
  $SSH "root@$LITE_IP" "
    if [ -x /data/opt/bin/yse ]; then
      /data/opt/bin/yse update
    else
      echo 'нет /data/opt/bin/yse — установите Entware вручную' >&2
      exit 1
    fi
  " || die "Entware не установить автоматически"
fi

say "opkg install shairport-sync nqptp avahi"
$SSH "root@$LITE_IP" "/opt/bin/opkg update && /opt/bin/opkg install shairport-sync nqptp avahi"

if [ "$HAS_PAYLOAD" = 1 ]; then
  say "Push /data/opt/airplay/"
  if [ -d "$PAYLOAD/opt/airplay" ]; then
    ( cd "$PAYLOAD/opt/airplay" && tar czf - . ) | \
      $SSH "root@$LITE_IP" "mkdir -p /data/opt/airplay && cd /data/opt/airplay && tar xzf -"
  fi

  if [ -f "$PAYLOAD/init/S99zairplay-lite1" ]; then
    say "Push S99zairplay-lite1"
    cat "$PAYLOAD/init/S99zairplay-lite1" | \
      $SSH "root@$LITE_IP" "cat > /etc/init.d/S99zairplay-lite1 && chmod 755 /etc/init.d/S99zairplay-lite1"

    say "Уникализация avahi host-name"
    MY_SN=$($SSH "root@$LITE_IP" "tr ' ' '\n' < /proc/cmdline | grep androidboot.serialno | cut -d= -f2" | tr -d '\r')
    if [ -n "$MY_SN" ]; then
      $SSH "root@$LITE_IP" "sed -i 's#yandexmicro-[A-Za-z0-9_-]*#yandexmicro-$MY_SN#g' /etc/init.d/S99zairplay-lite1" || true
      echo "  серийник: $MY_SN"
    else
      warn "не смог прочитать serialno — проверьте avahi host-name вручную"
    fi
    $SSH "root@$LITE_IP" "grep -n 'host-name\\|yandexmicro-' /etc/init.d/S99zairplay-lite1 || true"
  fi
fi

say "remount ro /"
$SSH "root@$LITE_IP" "sync; mount -o remount,ro /"

say "Старт AirPlay"
$SSH "root@$LITE_IP" "
  if [ -x /etc/init.d/S99zairplay-lite1 ]; then
    /etc/init.d/S99zairplay-lite1 start || echo 'S99zairplay-lite1 start вернул ошибку'
  else
    echo 'нет /etc/init.d/S99zairplay-lite1 (payload_airplay пуст?)'
  fi
  sleep 2
  ps | grep -E 'shairport|nqptp|avahi' | grep -v grep || echo 'ни один AirPlay-процесс не запущен'
"

cat <<EOF

============================================================
ГОТОВО. AirPlay запущен (или попытка сделана).

ПРОВЕРКА:
  - iPhone → AirPlay → колонка должна быть в списке
  - Логи: ssh -p $PORT root@$LITE_IP 'tail -f /tmp/shairport.log /tmp/nqptp.log'

ГРАБЛИ:
  - Звук только 48кГц (44.1 дрейфует)
  - avahi host-name должен быть уникальным (иначе iPhone увидит только один приёмник)
============================================================

EOF
