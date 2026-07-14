# ADR-0004: Cognito replaces hand-rolled auth

Date: 2026-07-13 · Status: Accepted

## Context

v1's auth was its weakest area: plaintext passwords in MySQL, hardcoded `admin@sjsu.edu`/`adminpassword`, session attributes for state, a half-wired Google Sign-In, no CSRF protection.

## Decision

Amazon Cognito user pool (50k MAU always-free) with Hosted UI:

- Email/password sign-up with verification; Google as a federated IdP (recreates v1's Google Sign-In properly).
- SPA uses Authorization Code + PKCE; API Gateway JWT authorizer validates tokens before Lambda is invoked.
- **Admin** = membership in a Cognito `admin` group, surfaced as a claim; Spring Security maps it to an authority guarding `/admin/**`.
- Backend never sees or stores credentials.

## Consequences

- Every security sin of v1 is fixed at the platform level rather than re-implemented.
- CSRF is moot (no cookies; bearer JWTs + CORS allowlist).
- Trade-off: Hosted UI styling is limited — acceptable; the backend is the showcase.
