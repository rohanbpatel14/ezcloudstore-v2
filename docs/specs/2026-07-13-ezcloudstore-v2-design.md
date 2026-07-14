# EzCloudStore v2 — Ground-Up Modern Rebuild (Java 21 + Serverless AWS)

## Context

EzCloudStore (2021, CMPE-281) is Rohan's flagship Java/AWS project: a 3-tier Spring Boot 2.5.5 / Java 8 / JSP / MySQL-RDS / EC2-Beanstalk file-storage app. It's referenced across 47+ archived conversations, the portfolio site, and the jarvis interview dossier — but the live deployment is gone and the code shows its age (plaintext passwords, hardcoded admin creds, business logic in the controller, `System.out.println` logging, one trivial test, AWS SDK v1).

**Goal:** rebuild it end-to-end as a live, interview-ready portfolio flagship demonstrating principal-level modern Java + AWS engineering — completely new code, informed by the old codebase and its archived documentation (LLD, code review, course PDFs, all locally available).

**Decisions locked with user:**
| Decision | Choice |
|---|---|
| Purpose | Portfolio showcase, live indefinitely |
| Budget | AWS free tier (~$0/mo standing cost) |
| Backend | The star — principal-grade rigor (hexagonal, DDD, TDD, ADRs) |
| Frontend | Clean minimal React SPA, proportionate to backend |
| v1 scope | Old-feature parity + modern core + **share links** + **file versioning** |
| v2+ | Folders, search, tags, quotas, activity log, AI auto-tagging → documented roadmap |
| AI | AI-assisted engineering workflow (spec-driven, TDD w/ Claude, CLAUDE.md, AI code review in CI) — not runtime AI features |
| Compute | **Spring Boot 3.x + Java 21 on Lambda with SnapStart** behind API Gateway HTTP API |

Old repo (reference only, never modified): `/Users/rohanpatel/Library/Mobile Documents/com~apple~CloudDocs/Desktop/desktop/DESKTOP/EzCloudStore/`
Supporting docs: `~/Downloads/EzCloudStore - CODE REVIEW.md`, `~/Downloads/EZCLOUDSTORE - SpringFramework-MVC-3-tier-architecture.md`, jarvis dossier at `~/Desktop/jarvis/dossiers/ezcloudstore.json`.

## Architecture

```
Browser ──► CloudFront ──► S3 (React SPA, static)
   │
   ├── Cognito Hosted UI (email/password + Google federation) ──► JWT
   │
   └── API Gateway HTTP API (JWT authorizer) ──► Lambda (Spring Boot 3, Java 21, SnapStart)
                                                    │
                              ┌─────────────────────┼──────────────────┐
                              ▼                     ▼                  ▼
                        DynamoDB (single table)   S3 files bucket    CloudWatch/X-Ray
                        metadata, share links     versioning ON      structured logs,
                        GSI for admin list-all    presigned PUT/GET  traces, metrics
```

**Key modernization moves vs the old app:**
- File bytes NEVER pass through the backend — S3 presigned PUT (≤10MB enforced via signed content-length condition) and presigned GET. Old app streamed everything through the servlet.
- Cognito replaces hand-rolled auth: fixes plaintext passwords, hardcoded `admin@sjsu.edu` admin, and recreates the old Google Sign-In properly via IdP federation. Admin = Cognito group claim.
- DynamoDB single-table replaces MySQL RDS (always-free 25GB vs unfree RDS); S3 native versioning powers file history; lifecycle rules (IA → Glacier) preserve the old project's data-lifecycle story.
- IaC end-to-end: AWS CDK **in Java** — the whole monorepo is one language.

## Monorepo layout (new repo `ezcloudstore` — fresh history, old repo untouched)

```
ezcloudstore/
├── CLAUDE.md                  # AI-assisted engineering conventions
├── docs/
│   ├── adr/                   # ADR-0001 serverless, 0002 dynamodb, 0003 presigned, ...
│   ├── specs/                 # this design as the founding spec
│   └── ROADMAP.md             # v2+: folders, search, tags, quotas, activity log, AI tagging
├── backend/                   # Spring Boot 3.x, Java 21, hexagonal
│   └── src/main/java/com/ezcloudstore/
│       ├── domain/            # pure: File, FileVersion, ShareLink, StorageQuota(v2) + ports
│       ├── application/       # use cases: UploadFile, ListFiles, CreateShareLink, ...
│       ├── adapters/
│       │   ├── in/rest/       # controllers, OpenAPI, request/response DTOs
│       │   └── out/           # DynamoDbFileRepository, S3PresignedStorage, ...
│       └── config/
├── frontend/                  # React + Vite + TS, minimal clean UI
├── infra/                     # CDK app (Java): StatefulStack, ApiStack, WebStack, CiStack
└── .github/workflows/         # ci.yml (test+archunit), review.yml (Claude), deploy.yml
```

## Domain model & API surface (v1)

Entities: `UserProfile` (from Cognito claims), `StoredFile` (id, ownerId, name, description, size, contentType, createdAt, updatedAt, currentVersionId), `FileVersion` (s3VersionId, size, createdAt), `ShareLink` (token, fileId, expiresAt, createdBy).

```
POST   /files                    → init upload: returns presigned PUT + fileId
POST   /files/{id}/complete      → confirm upload, persist metadata (S3 HEAD verify)
GET    /files                    → list own files (paginated)
GET    /files/{id}               → metadata + versions
GET    /files/{id}/download      → presigned GET (optionally ?versionId=)
PUT    /files/{id}               → update description / new version (presigned PUT flow)
DELETE /files/{id}               → delete (all versions + metadata)
POST   /files/{id}/shares        → create expiring share link
DELETE /shares/{token}           → revoke
GET    /public/shares/{token}    → resolve → presigned GET (unauthenticated route)
GET    /admin/files              → all users' files (Cognito `admin` group only)
DELETE /admin/files/{id}         → admin delete
```

DynamoDB single table `ezcloudstore`: `PK=USER#<sub> SK=FILE#<id>`, `PK=FILE#<id> SK=VERSION#<ts>`, `PK=SHARE#<token> SK=META` (TTL on expiresAt), `GSI1 PK=ENTITY#FILE` for admin list-all.

## Engineering practices (the actual showcase)

- **TDD throughout** (superpowers test-driven-development skill): domain + application layers built red-green-refactor; ArchUnit tests enforce hexagonal dependency rules (domain imports nothing from Spring/AWS).
- **Testing pyramid:** JUnit 5 + AssertJ unit tests (domain, no mocks needed by design); Testcontainers + LocalStack integration tests for DynamoDB/S3 adapters; MockMvc slice tests for REST; one post-deploy smoke E2E (GitHub Action hits health + full upload/share/download flow).
- **AI-native workflow:** CLAUDE.md with conventions; spec + ADRs written before code; Claude Code GitHub Action for PR review; conventional commits.
- **Observability:** Lambda Powertools for Java (structured JSON logging, X-Ray tracing, EMF metrics); correlation IDs; CloudWatch dashboard + alarm (errors > threshold → SNS email — echoes old project's CloudWatch/SNS story).
- **Security:** least-privilege IAM per stack; S3 buckets fully private (presigned-only); JWT authorizer at the gateway; no secrets in code (SSM if ever needed); dependency scanning via Dependabot.

## Cost guardrails (free tier)

Lambda 1M req/mo + DynamoDB 25GB + Cognito 50k MAU + CloudFront 1TB/mo: always-free. S3: pennies at portfolio scale. API Gateway HTTP API: $1/M requests after first year — effectively $0. X-Ray 100k traces free. **Only real optional cost:** re-registering `ezcloudstore.com` (~$14/yr + $0.50/mo hosted zone) — decision deferred to deploy phase; CloudFront default domain works free.

## Implementation phases

1. **Scaffold + spec** — monorepo, CLAUDE.md, founding ADRs, design doc committed, CI skeleton (build + test gates), CDK bootstrap.
2. **Domain core (TDD)** — entities, ports, use cases; ArchUnit rules; 100% of domain behavior test-first. No AWS yet.
3. **Adapters (TDD w/ LocalStack)** — DynamoDB repo, S3 presigned storage, REST controllers + OpenAPI; Cognito JWT security config.
4. **Infra (CDK Java)** — stateful stack (table, buckets), API stack (Lambda SnapStart + gateway + authorizer), web stack (SPA hosting), deploy from CI. First live deploy at end of this phase.
5. **Frontend** — Vite/React/TS: auth flow via Cognito Hosted UI, file dashboard, upload w/ progress, versions, share-link UX, admin view.
6. **Hardening + launch** — smoke E2E in CI, CloudWatch dashboard/alarm, README with architecture diagram, ROADMAP.md, record new demo video, update portfolio site + jarvis dossier links.

Each phase lands via PR with AI review + green CI. Phases 2–3 are where TDD discipline is strictest.

## Verification

- `mvn verify` green: unit + ArchUnit + Testcontainers integration suites.
- `cdk synth` clean; `cdk deploy` from CI to a fresh stack; post-deploy smoke workflow passes (register → upload → share → download via public link → version → delete).
- Manual: full user journey on the live URL in a clean browser; admin journey with a group-assigned user; verify $0.00 forecast in Cost Explorer after 48h.
- Lighthouse pass on the SPA; OpenAPI renders in Swagger UI.

## Out of scope for v1 (→ ROADMAP.md)

Folders/hierarchy, full-text & semantic search, tags, per-user quotas, activity log, AI auto-tagging/summarization (Claude API), multi-region DR, Terraform variant.

## Open items (resolve during execution, non-blocking)

- Domain: reuse `ezcloudstore.com` (paid) vs free CloudFront URL — deploy-phase decision.
- New GitHub repo name/visibility (suggest public `ezcloudstore`, archive old repo with a pointer README).
- AWS account/region confirmation before `cdk bootstrap` (suggest `us-west-1`/`us-east-1` — old project used us-west-1).
