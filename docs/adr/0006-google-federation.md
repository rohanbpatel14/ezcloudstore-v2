# ADR-0006: Google sign-in via Cognito federation

Date: 2026-07-14 · Status: Accepted

## Context

The 2021 app had a hand-rolled Google Sign-In. v1 of this rebuild (ADR-0004)
stood up Cognito but left Google federation as a placeholder, so the live
system only did email/password — a gap versus the original.

## Decision

Wire Google as a Cognito federated IdP (`UserPoolIdentityProviderGoogle`),
attached to the SPA app client's supported providers. Because federation
requires an OAuth client that only exists once the developer creates it in
Google Cloud Console, the CDK gates it on a context value:

- `cdk deploy -c googleClientId=<id>.apps.googleusercontent.com` enables it.
- Without the flag the stack deploys with email/password only — no broken half-state.

The Google **client secret is never in git or the template**: it's read at
deploy time from the SSM SecureString `/ezcloudstore/google-client-secret`
via `SecretValue.ssmSecure`, so CloudFormation stores only a `resolve:ssm-secure`
reference. This is the same "no secrets in code" rule that ADR-0004 established,
applied to the one secret federation needs.

## Operator steps (one time)

1. Google Cloud Console → Credentials → create an OAuth 2.0 Client (Web).
2. Authorized redirect URI: `https://ezcloudstore.auth.us-east-2.amazoncognito.com/oauth2/idpresponse`.
3. `aws ssm put-parameter --name /ezcloudstore/google-client-secret --type SecureString --value <secret> --region us-east-2`
4. Deploy Auth + Web with `-c googleClientId=<client-id>`, then the Hosted UI shows "Continue with Google".

## Consequences

- Closes the federation gap without ever committing a secret.
- The SPA needs no change — the Hosted UI renders the Google button once the IdP is attached.
- Trade-off: enabling Google is a deliberate operator action, not automatic — correct, since the credentials are owned outside this repo.
