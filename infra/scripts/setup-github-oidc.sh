#!/usr/bin/env bash
# Creates the GitHub OIDC identity provider + least-privilege deploy role,
# wires the repo secret, and re-enables the Deploy workflow.
# The role can ONLY assume CDK bootstrap roles — no direct resource access.
set -euo pipefail

REPO="rohanbpatel14/ezcloudstore-v2"
ROLE_NAME="ezcloudstore-github-deploy"
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)

echo "==> Ensuring GitHub OIDC provider exists in account $ACCOUNT"
if ! aws iam list-open-id-connect-providers --output text | grep -q token.actions.githubusercontent.com; then
  aws iam create-open-id-connect-provider \
    --url https://token.actions.githubusercontent.com \
    --client-id-list sts.amazonaws.com \
    --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
fi

echo "==> Creating role $ROLE_NAME (trusts only $REPO on main / production env)"
TRUST=$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Federated": "arn:aws:iam::${ACCOUNT}:oidc-provider/token.actions.githubusercontent.com"},
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {"token.actions.githubusercontent.com:aud": "sts.amazonaws.com"},
      "StringLike": {"token.actions.githubusercontent.com:sub": [
        "repo:${REPO}:environment:production",
        "repo:${REPO}:ref:refs/heads/main"
      ]}
    }
  }]
}
EOF
)
aws iam create-role --role-name "$ROLE_NAME" \
  --description "GitHub Actions OIDC deploy role for ${REPO} (assumes CDK bootstrap roles only)" \
  --assume-role-policy-document "$TRUST"

POLICY=$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AssumeCdkBootstrapRoles",
    "Effect": "Allow",
    "Action": "sts:AssumeRole",
    "Resource": "arn:aws:iam::${ACCOUNT}:role/cdk-*"
  }]
}
EOF
)
aws iam put-role-policy --role-name "$ROLE_NAME" \
  --policy-name assume-cdk-roles --policy-document "$POLICY"

echo "==> Wiring repo secret + re-enabling Deploy workflow"
gh secret set AWS_DEPLOY_ROLE_ARN --body "arn:aws:iam::${ACCOUNT}:role/${ROLE_NAME}" -R "$REPO"
gh workflow enable Deploy -R "$REPO"

echo "==> Done. Trigger a deploy with: gh workflow run Deploy -R $REPO"
