#!/usr/bin/env bash
# Post-deploy smoke test: exercises the unauthenticated surface of the live
# API and asserts the security boundary holds. Auth'd flows (upload/share)
# need a real Cognito token and are covered by the LocalStack integration
# suite in CI; this script is the "is the deployment actually alive and
# locked down" check.
#
# Usage: API_URL=https://xxxx.execute-api.us-east-2.amazonaws.com bash smoke-test.sh
set -euo pipefail

API_URL="${API_URL:-$(aws cloudformation describe-stacks --region "${AWS_REGION:-us-east-2}" \
  --stack-name EzCloudStoreApi \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)}"

echo "Smoke-testing $API_URL"
fail=0

check() {
  local name="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "  ok   $name ($actual)"
  else
    echo "  FAIL $name — expected $expected, got $actual"
    fail=1
  fi
}

code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

# 1. Health endpoint is public and green
check "health is 200"                 200 "$(code "$API_URL/actuator/health")"
# 2. Listing files without a token is rejected at the gateway
check "GET /files unauthenticated"    401 "$(code "$API_URL/files")"
# 3. Admin surface rejects anonymous callers
check "GET /admin/files anonymous"    401 "$(code "$API_URL/admin/files")"
# 4. Unknown share token resolves to 404 (not 500 — the not-found path works)
check "unknown share token is 404"    404 "$(code "$API_URL/public/shares/definitely-not-a-real-token")"

if [ "$fail" -ne 0 ]; then
  echo "Smoke test FAILED"
  exit 1
fi
echo "Smoke test passed."
