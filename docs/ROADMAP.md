# EzCloudStore Roadmap

## v1 (current) — parity + modern core

- Cognito auth (email/password + Google federation), admin via group claim
- File upload ≤10MB via S3 presigned PUT; download via presigned GET
- List / update (description + new version) / delete own files
- File version history (S3 versioning)
- Expiring share links (public resolve endpoint, DynamoDB TTL)
- Admin: list all files, delete any file
- Serverless: Spring Boot 3 on Lambda (SnapStart), API Gateway HTTP API, DynamoDB single-table, CloudFront SPA

## v2 — organization

- Folders / hierarchy
- Tags and file metadata search
- Per-user storage quotas
- Activity log (event-sourced from DynamoDB streams)

## v3 — intelligence

- AI auto-tagging & summarization of uploads (Claude API)
- Semantic search across file contents
- Anomaly alerts on unusual access patterns

## Explicitly rejected for now

- Multi-region active-active (cost, YAGNI at portfolio scale)
- Microservice split (the domain is one bounded context)
- Kubernetes (violates free-tier constraint; serverless tells a better story here)
