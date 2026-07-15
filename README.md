# EzCloudStore v2

Serverless cloud file storage on AWS — a ground-up rebuild of my 2021 CMPE-281 project with 2026 engineering standards: Java 21, Spring Boot 3 on Lambda (SnapStart), DynamoDB single-table, S3 presigned transfers, Cognito auth, CDK-in-Java infrastructure, and TDD throughout.

**Live app:** https://d2ymqh0865i28q.cloudfront.net · **API base:** https://j9ac8b4mj5.execute-api.us-east-2.amazonaws.com · **Region:** us-east-2 (AWS free tier)

```
Browser ──► CloudFront (geo-restricted) ──► S3 (React SPA)
   │
   ├── Cognito Hosted UI (email/password + Google federation) ──► JWT
   │
   └── API Gateway HTTP API (JWT authorizer) ──► Lambda (Spring Boot 3, Java 21, SnapStart, X-Ray)
                                                    │
                              ┌─────────────────────┼──────────────────┐
                              ▼                     ▼                  ▼
                        DynamoDB (single table)   S3 files bucket    CloudWatch alarms +
                        metadata + share links    versioning ON      SNS alerts + dashboard
                        GSI for admin listing     presigned PUT/GET   + AWS Budgets guard
```

## Why a rebuild?

The [original](https://youtu.be/JROeVGt9_dg) was a 3-tier Spring Boot 2.5/Java 8 monolith on Elastic Beanstalk + RDS. It worked — but it streamed file bytes through the servlet, stored plaintext passwords, hardcoded its admin login, and was deployed by console clicks. v2 keeps the product (upload, version, share, manage files) and replaces every mechanism with the current best practice. The delta *is* the portfolio piece — see [docs/specs](docs/specs/) and [docs/adr](docs/adr/) for every decision with rationale.

| | v1 (2021) | v2 (2026) |
|---|---|---|
| Compute | EC2 + Elastic Beanstalk (always-on) | Lambda + SnapStart (scale-to-zero) |
| Data | MySQL RDS | DynamoDB single-table |
| File transfer | bytes through the servlet | S3 presigned PUT/GET only |
| Auth | plaintext passwords, hardcoded admin | Cognito + Google federation, group-based admin |
| Infra | console clicks | CDK (Java), deployed from CI via OIDC |
| Tests | one `assertTrue(true)` | TDD; unit + ArchUnit + Testcontainers + smoke E2E |
| Architecture | logic in the controller | hexagonal, enforced by ArchUnit |

## Monorepo

- [`backend/`](backend/) — Spring Boot 3, hexagonal (domain / application / adapters)
- [`frontend/`](frontend/) — React + Vite + TypeScript SPA
- [`infra/`](infra/) — AWS CDK (Java) stacks
- [`docs/`](docs/) — design spec, ADRs, roadmap

## Local development

```bash
./mvnw -B verify              # backend + infra: all tests incl. ArchUnit
cd frontend && npm ci && npm run build
cd infra && cdk synth         # infra dry-run (no AWS credentials needed)
```

Requires: JDK 21+, Node 20+, Docker (integration tests), AWS CDK CLI.

## Status

✅ v1 shipped and live on AWS (us-east-2), deployed from CDK. Next features tracked in [ROADMAP](docs/ROADMAP.md).
