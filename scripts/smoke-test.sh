#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
mkdir -p docs/homework/week-06/evidence
python3 scripts/smoke-test.py normal | tee docs/homework/week-06/evidence/normal.txt
if [[ "${1:-}" == "--with-failure" ]]; then
  restore() { docker compose start user-service >/dev/null; }
  trap restore EXIT
  echo '$ docker compose stop user-service'
  docker compose stop user-service
  python3 scripts/smoke-test.py outage | tee docs/homework/week-06/evidence/outage.txt
  echo '$ docker compose start user-service'
  docker compose start user-service
  python3 scripts/smoke-test.py recovery | tee docs/homework/week-06/evidence/recovery.txt
  trap - EXIT
fi
