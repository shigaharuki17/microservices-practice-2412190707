#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
out=docs/homework/week-08/evidence
mkdir -p "$out"
if docker info >/dev/null 2>&1; then engine=(docker); else
  echo '需要 Docker 权限来停止和恢复本项目的住户服务。'
  sudo -v
  engine=(sudo docker)
fi
stopped=0
finish() {
  result=$?
  if [[ "$stopped" == 1 ]]; then "${engine[@]}" compose start user-service user-service-2 >/dev/null || result=1; fi
  # Keep generated evidence writable when a user invokes this script with sudo.
  if [[ "$EUID" == 0 && -n "${SUDO_USER:-}" && "$SUDO_USER" != root ]]; then
    find "$out" -maxdepth 1 -type f -exec chown "$SUDO_USER" {} +
    chown "$SUDO_USER" "$out"
  fi
  trap - EXIT
  exit "$result"
}
trap finish EXIT
python3 scripts/e2e-test.py normal | tee "$out/normal.txt"
stopped=1
"${engine[@]}" compose stop user-service user-service-2
python3 scripts/e2e-test.py outage | tee "$out/outage.txt"
"${engine[@]}" compose start user-service user-service-2
python3 scripts/e2e-test.py recovery | tee "$out/recovery.txt"
stopped=0
echo E2E_ALL=PASS
