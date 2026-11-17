#!/bin/bash
# save_to_payloads.sh — копирует рабочие файлы с рутованной колонки в payload_* папки.
# Использование:
#   ./save_to_payloads.sh                          # REF_IP=192.168.1.115
#   REF_IP=192.168.1.122 ./save_to_payloads.sh     # Розовая
#   REF_IP=192.168.1.113 ./save_to_payloads.sh     # Душ (discovery, без -s)
#   AIRPLAY_IP=192.168.1.126 ./save_to_payloads.sh # + AirPlay
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
REF_IP="${REF_IP:-192.168.1.115}"
AIRPLAY_IP="${AIRPLAY_IP:-}"
PORT=2222
SSH_OPTS="-p $PORT -o ConnectTimeout=5 -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"

say()  { printf '\n\033[1;36m[save]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[ERR]\033[0m %s\n' "$*" >&2; exit 1; }

command -v ssh >/dev/null || die "нужен ssh"

grab() {
  local src="$1" dst="$2"
  mkdir -p "$(dirname "$dst")"
  if ssh $SSH_OPTS "root@$REF_IP" "test -e '$src'" 2>/dev/null; then
    ssh $SSH_OPTS "root@$REF_IP" "cat '$src'" > "$dst" 2>/dev/null
    local sz; sz=$(stat -c%s "$dst" 2>/dev/null || echo 0)
    if [ "$sz" = 0 ]; then
      rm -f "$dst"
      warn "  ✗ $src скачался ПУСТЫМ (обрыв SSH?) — $dst удалён, перезапустите"
    else
      say "  ✓ $src → $dst ($sz B)"
    fi
  else
    warn "  ✗ $src отсутствует на $REF_IP — пропуск"
  fi
}

grab_dir() {
  local src="$1" dst="$2"
  mkdir -p "$dst"
  if ssh $SSH_OPTS "root@$REF_IP" "test -d '$src'" 2>/dev/null; then
    ssh $SSH_OPTS "root@$REF_IP" "cd '$src' && tar czf - ." 2>/dev/null \
      | tar xzf - -C "$dst"
    say "  ✓ $src/ → $dst/"
  else
    warn "  ✗ $src/ отсутствует на $REF_IP — пропуск"
  fi
}

say "=== payload_root ($REF_IP) ==="
grab /data/opt/bin/dropbear          "$HERE/payload_root/ssh/dropbear"
grab /data/opt/bin/dropbearkey       "$HERE/payload_root/ssh/dropbearkey"
grab /etc/init.d/S99zdropbear        "$HERE/payload_root/init/S99zdropbear"
grab /data/opt/yandex-noupdate.sh    "$HERE/payload_root/bin/yandex-noupdate.sh"

say "=== payload_squeezelite ($REF_IP) ==="
grab /data/opt/bin/squeezelite       "$HERE/payload_squeezelite/bin/squeezelite"
grab /etc/init.d/S98squeeze          "$HERE/payload_squeezelite/init/S98squeeze"

say "=== payload_local_commands ($REF_IP) ==="
grab /data/opt/bin/whatplays.sh      "$HERE/payload_local_commands/bin/whatplays.sh"
grab /data/opt/bin/alice_ipc_mon     "$HERE/payload_local_commands/bin/alice_ipc_mon"
grab /data/opt/bin/tcpcat            "$HERE/payload_local_commands/bin/tcpcat"
grab /etc/init.d/S97whatplays        "$HERE/payload_local_commands/init/S97whatplays"

if [ -z "$AIRPLAY_IP" ]; then
  warn ""
  warn "AIRPLAY_IP не задан — payload_airplay не заполняем."
  warn "Если есть колонка с AirPlay: AIRPLAY_IP=<ip> $0"
else
  say "=== payload_airplay ($AIRPLAY_IP) ==="
  SAVED="$REF_IP"; REF_IP="$AIRPLAY_IP"
  grab /etc/init.d/S99zairplay-lite1 "$HERE/payload_airplay/init/S99zairplay-lite1"
  grab_dir /data/opt/airplay          "$HERE/payload_airplay/opt/airplay"
  REF_IP="$SAVED"
fi

say "=== Контрольные суммы ==="
for p in payload_root payload_airplay payload_squeezelite payload_local_commands; do
  if [ -d "$HERE/$p" ] && [ -n "$(find "$HERE/$p" -type f 2>/dev/null | head -1)" ]; then
    ( cd "$HERE/$p" && find . -type f ! -name SHA256SUMS -exec sha256sum {} \; > SHA256SUMS )
    printf '  %s: %s файлов\n' "$p" "$(wc -l < "$HERE/$p/SHA256SUMS")"
  fi
done

say "=== Готово ==="
for p in payload_root payload_airplay payload_squeezelite payload_local_commands; do
  echo "--- $p ---"
  find "$HERE/$p" -type f 2>/dev/null | sed "s#$HERE/##"
done
