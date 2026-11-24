#!/usr/bin/env bash
# Kiểm tra toàn bộ DocReel (PLAN 2.7). Thoát mã 1 nếu có bước KHÔNG ĐẠT.
#   scripts/check.sh [--media] [--e2e]
set -u

MEDIA=0
E2E=0
for arg in "$@"; do
  case "$arg" in
    --media|-Media) MEDIA=1 ;;
    --e2e|-E2E) E2E=1 ;;
    *) echo "Tham số lạ: $arg (dùng --media, --e2e)"; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

NAMES=()
RESULTS=()

step() {
  local name="$1"; shift
  echo
  echo "==> $name"
  if "$@"; then
    NAMES+=("$name"); RESULTS+=("ĐẠT"); return 0
  else
    NAMES+=("$name"); RESULTS+=("KHÔNG ĐẠT"); return 1
  fi
}

skip() {
  NAMES+=("$1"); RESULTS+=("BỎ QUA ($2)")
}

# 1. Định dạng code
step "spotless:check" ./mvnw -B -q spotless:check

# 2. Build + test: compile -Werror, ArchUnit, unit, integration, contract, JaCoCo
step "mvnw verify" ./mvnw -B verify

# 3. Web
web_ready=1
if [ ! -d web/node_modules ]; then
  step "web: npm ci" bash -c 'cd web && npm ci' || web_ready=0
fi
if [ "$web_ready" -eq 1 ]; then
  step "web: lint" bash -c 'cd web && npm run lint'
  step "web: typecheck" bash -c 'cd web && npm run typecheck'
  step "web: test" bash -c 'cd web && npm test'
  step "web: build" bash -c 'cd web && npm run build'
else
  for s in "web: lint" "web: typecheck" "web: test" "web: build"; do skip "$s" "npm ci lỗi"; done
fi

# 4–5. Tùy chọn
[ "$MEDIA" -eq 1 ] && step "mvnw verify -Pmedia" ./mvnw -B verify -Pmedia
[ "$E2E" -eq 1 ] && step "mvnw verify -Pe2e" ./mvnw -B verify -Pe2e

echo
echo "==================== TỔNG KẾT ===================="
failed=0
for i in "${!NAMES[@]}"; do
  printf '  %-24s %s\n' "${NAMES[$i]}" "${RESULTS[$i]}"
  [ "${RESULTS[$i]}" != "ĐẠT" ] && failed=$((failed + 1))
done
if [ "$failed" -gt 0 ]; then
  echo "KẾT QUẢ: KHÔNG ĐẠT ($failed bước)"
  exit 1
fi
echo "KẾT QUẢ: ĐẠT"
exit 0