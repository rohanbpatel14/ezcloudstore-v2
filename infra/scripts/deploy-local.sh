#!/usr/bin/env bash
# One-shot local deploy of all four stacks + frontend rebuild against live outputs.
# Prereqs: aws login done, cdk bootstrap done (both already true on this machine).
set -euo pipefail

REGION="${AWS_REGION:-us-east-2}"
export AWS_REGION="$REGION" AWS_DEFAULT_REGION="$REGION"

cd "$(dirname "$0")/.."   # infra/

echo "==> Building backend Lambda zip"
(cd .. && ./mvnw -B -q -pl backend package -DskipTests)

echo "==> Placeholder frontend build (Web stack needs a dist to ship)"
(cd ../frontend && npm run build >/dev/null)

echo "==> Deploying Stateful + Auth + Api (Web deploys as a dependency)"
cdk deploy EzCloudStoreStateful EzCloudStoreAuth EzCloudStoreApi \
  --require-approval never --outputs-file outputs.json

echo "==> Rebuilding frontend against live outputs"
API_URL=$(jq -r '.EzCloudStoreApi.ApiUrl' outputs.json)
CLIENT_ID=$(jq -r '.EzCloudStoreAuth.SpaClientId' outputs.json)
DOMAIN_PREFIX=$(jq -r '.EzCloudStoreAuth.HostedUiDomainName' outputs.json)
(cd ../frontend && \
  VITE_API_URL="$API_URL" \
  VITE_COGNITO_CLIENT_ID="$CLIENT_ID" \
  VITE_COGNITO_DOMAIN="${DOMAIN_PREFIX}.auth.${REGION}.amazoncognito.com" \
  npm run build)

echo "==> Deploying Web stack with the real frontend"
cdk deploy EzCloudStoreWeb --require-approval never

echo
echo "==> Done. Live URL:"
aws cloudformation describe-stacks --stack-name EzCloudStoreWeb \
  --query "Stacks[0].Outputs[?OutputKey=='WebUrl'].OutputValue" --output text
