#!/bin/bash
# root.sh — минимальный рут после stand.sh.
# Использование: ./root.sh <IP_колонки>
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PAYLOAD="$HERE/payload_root"

LITE_IP="${1:-}"
[ -n "$LITE_IP" ] || { echo "использование: $0 <IP_колонки>"; exit 1; }

SSH_PORT=2222
ADB_PORT=5555

say()  { printf '\n\033[1;36m[root]\033[0m %s\n' "$*"; }
warn() { printf '\n\033[1;33m[WARN]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[ERR]\033[0m %s\n' "$*" >&2; exit 1; }

command -v adb >/dev/null || die "adb не установлен"
command -v ssh >/dev/null || die "ssh не установлен (apt install openssh-client)"
[ -s "$PAYLOAD/ssh/dropbear" ] || die "нет/пуст $PAYLOAD/ssh/dropbear — запустите save_to_payloads.sh"
[ -s "$PAYLOAD/init/S99zdropbear" ] || die "нет/пуст $PAYLOAD/init/S99zdropbear"

# --- Защита от повторного запуска ---
say "Проверка: не рутована ли уже $LITE_IP"
if ssh -p "$SSH_PORT" -o ConnectTimeout=3 -o BatchMode=yes -o StrictHostKeyChecking=no \
       -o UserKnownHostsFile=/dev/null "root@$LITE_IP" "true" 2>/dev/null; then
  die "колонка $LITE_IP уже рутована (SSH работает). root.sh повторно НЕ запускать."
fi

say "ADB connect $LITE_IP:$ADB_PORT"
adb connect "$LITE_IP:$ADB_PORT" >/dev/null 2>&1 || true

say "Ожидание ADB root (до 5 мин)..."
ok=0
for i in $(seq 1 60); do
  if adb shell true 2>/dev/null; then ok=1; break; fi
  sleep 5
done
[ "$ok" = 1 ] || die "ADB не поднялся. Проверьте DNS, сброс, экран Wi-Fi."

ID=$(adb shell id 2>/dev/null || true)
echo "adb shell id: $ID"
echo "$ID" | grep -q 'uid=0(root)' || die "ADB без root — прошивка не .46 или оффер не пришёл."

say "remount rw /"
adb shell "mount -o remount,rw /" || die "remount rw не удался"

say "dropbear + S99zdropbear"
adb push "$PAYLOAD/ssh/dropbear"      /data/local/tmp/dropbear
adb push "$PAYLOAD/ssh/dropbearkey"   /data/local/tmp/dropbearkey 2>/dev/null || true
adb push "$PAYLOAD/init/S99zdropbear" /data/local/tmp/S99zdropbear

adb shell "chmod 755 /data/local/tmp/dropbear /data/local/tmp/S99zdropbear"
adb shell "[ -f /data/local/tmp/dropbearkey ] && chmod 755 /data/local/tmp/dropbearkey" || true

adb shell "mkdir -p /data/opt/bin /data/opt/etc/dropbear /etc/dropbear"
adb shell "cp /data/local/tmp/dropbear /data/opt/bin/dropbear"
adb shell "[ -f /data/local/tmp/dropbearkey ] && cp /data/local/tmp/dropbearkey /data/opt/bin/dropbearkey" || true
adb shell "chmod 755 /data/opt/bin/dropbear /data/opt/bin/dropbearkey 2>/dev/null || true"

adb shell "cp /data/local/tmp/S99zdropbear /etc/init.d/S99zdropbear"
adb shell "chmod 755 /etc/init.d/S99zdropbear"

adb shell "[ -f /etc/passwd ] && grep -q '^root:' /etc/passwd || echo 'root::0:0:root:/root:/bin/sh' >> /etc/passwd"
adb shell "[ -f /etc/group ] && grep -q '^root:' /etc/group || echo 'root:x:0:' >> /etc/group"

say "OTA-off"
if [ -f "$PAYLOAD/bin/yandex-noupdate.sh" ]; then
  adb push "$PAYLOAD/bin/yandex-noupdate.sh" /data/local/tmp/yandex-noupdate.sh
  adb shell "chmod 755 /data/local/tmp/yandex-noupdate.sh"
  adb shell "sh /data/local/tmp/yandex-noupdate.sh" || warn "yandex-noupdate вернул ошибку — проверьте вручную"

  # постоянная копия для status/restore
  adb shell "cp /data/local/tmp/yandex-noupdate.sh /data/opt/yandex-noupdate.sh"
  adb shell "chmod 755 /data/opt/yandex-noupdate.sh"
else
  warn "yandex-noupdate.sh в payload_root/bin нет — пропускаю OTA-off"
fi

say "Маркеры activate_* +i"
adb shell "mkdir -p /data/quasar"
for m in activate_adb_quasmodrom activate_ssh; do
  adb shell "touch /data/quasar/$m"
  adb shell "chattr +i /data/quasar/$m 2>/dev/null || true"
done

say "remount ro /"
adb shell "sync; mount -o remount,ro /" || warn "remount ro не удался"

# timeout + </dev/null: adb shell не блокируется на dropbear
say "Старт dropbear (timeout 15s)"
timeout 15 adb shell "/etc/init.d/S99zdropbear start" </dev/null >/dev/null 2>&1 || true
sleep 3

say "Диагностика dropbear"
adb shell "ps | grep -E '[d]ropbear' || echo 'dropbear НЕ запущен'" 2>/dev/null || true

say "Проверка SSH"
for i in 1 2 3 4 5; do
  if ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
         -o BatchMode=yes -o ConnectTimeout=5 "root@$LITE_IP" "id" 2>/dev/null | grep -q 'uid=0'; then
    say "SSH работает"
    break
  fi
  [ "$i" = 5 ] && warn "SSH пока молчит — после ребута поднимется через S99zdropbear"
  sleep 3
done

cat <<EOF

============================================================
ГОТОВО. Рут на $LITE_IP есть.
  adb connect $LITE_IP:$ADB_PORT && adb shell id
  ssh -p $SSH_PORT root@$LITE_IP

ВАЖНО: если yandex-noupdate.sh написал "Перезагрузи колонку" — сделайте:
  adb reboot
  sleep 90
  ssh -p $SSH_PORT root@$LITE_IP "id"   # должно быть uid=0(root)

ДАЛЬШЕ (независимо):
  ./squeezelite.sh <IP> [--name "Yandex Lite SL"]
  ./local_commands.sh <IP> --room <Имя> --aliceid <UUID>
  ./airplay.sh <IP>    # опционально
============================================================

EOF
