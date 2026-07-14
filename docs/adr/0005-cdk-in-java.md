# ADR-0005: Infrastructure as code with AWS CDK in Java

Date: 2026-07-13 · Status: Accepted

## Context

v1 was deployed by console clicks (Beanstalk wizard, manual Route53/CloudFront setup) — undocumented, unreproducible, and gone the moment the account was cleaned up. The rebuild must be reproducible from a fresh account.

## Decision

AWS CDK v2 with **Java** as the stack language — the entire monorepo stays one language. Stacks: `StatefulStack` (DynamoDB + S3, retain-on-delete), `AuthStack` (Cognito), `ApiStack` (Lambda + HTTP API), `WebStack` (SPA bucket + CloudFront). CI deploys via `cdk deploy` with an OIDC-assumed role — no long-lived AWS keys in GitHub.

## Alternatives

Terraform (industry-standard, but splits the repo into HCL); SAM (Lambda-centric, weaker for the non-Lambda stacks); Console (how v1 died).

## Consequences

- `cdk synth` runs in CI as a gate — infra is compiled and type-checked like code, and unit-testable with CDK assertions.
- Anyone (including future interviewers) can stand up the entire system with `cdk deploy`.
