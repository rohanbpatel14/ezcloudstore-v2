# EzCloudStore v2 — Engineering Conventions

Monorepo for EzCloudStore v2: serverless file storage on AWS. Java 21 + Spring Boot 3 backend (hexagonal), React SPA frontend, CDK-in-Java infra.

## Commands

- Backend build + all tests: `./mvnw -B verify` (root; builds backend + infra)
- Backend only: `./mvnw -B -pl backend verify`
- Frontend: `cd frontend && npm ci && npm run typecheck && npm run build`
- Infra synth: `cd infra && cdk synth` (requires built jar; no AWS creds needed for synth)

## Non-negotiables

1. **TDD** — no production code without a failing test first (red-green-refactor). Domain and application layers especially: write the test, watch it fail, make it pass, refactor.
2. **Hexagonal boundaries** — enforced by `HexagonalArchitectureTest` (ArchUnit):
   - `domain/` imports nothing from Spring, AWS SDK, or persistence frameworks.
   - `application/` (use cases) depends only on `domain/`.
   - `adapters/in/*` and `adapters/out/*` depend inward, never on each other.
3. **File bytes never transit the backend** — uploads/downloads use S3 presigned URLs only.
4. **No secrets in code or config files** — Cognito handles credentials; anything else goes to SSM.
5. **Conventional commits** — `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`, `ci:`. Scope optional: `feat(share-links): ...`
6. **ADRs** — any decision that changes architecture, a dependency, or an AWS service choice gets an ADR in `docs/adr/` before the code lands.

## Layout

- `backend/src/main/java/com/ezcloudstore/domain/` — entities + ports (pure Java)
- `backend/src/main/java/com/ezcloudstore/application/` — use cases
- `backend/src/main/java/com/ezcloudstore/adapters/in/rest/` — controllers, DTOs
- `backend/src/main/java/com/ezcloudstore/adapters/out/` — dynamodb/, s3/
- `infra/src/main/java/com/ezcloudstore/infra/` — CDK stacks
- `docs/specs/` — design specs; `docs/adr/` — decision records

## Testing

- Unit: JUnit 5 + AssertJ. Domain tests need no mocking framework by design — use hand-rolled test doubles for ports.
- Integration: Testcontainers + LocalStack for DynamoDB/S3 adapters (requires Docker).
- REST: MockMvc slice tests.
- Architecture: ArchUnit (runs in the normal test phase).

## Reference material (read-only)

Original 2021 project (never modify): `/Users/rohanpatel/Library/Mobile Documents/com~apple~CloudDocs/Desktop/desktop/DESKTOP/EzCloudStore/`
Its LLD and code review live in `~/Downloads/EZCLOUDSTORE - SpringFramework-MVC-3-tier-architecture.md` and `~/Downloads/EzCloudStore - CODE REVIEW.md`.
