# ADR-0003: File bytes never transit the backend — S3 presigned URLs

Date: 2026-07-13 · Status: Accepted

## Context

v1 streamed every upload/download through the servlet (`Part` → byte[] → S3), holding file bytes in JVM memory. On Lambda this would burn duration, memory, and the 6MB payload limit.

## Decision

- **Upload:** `POST /files` returns a presigned PUT URL (content-length-range condition enforces the 10MB cap at S3 itself); client PUTs directly to S3; `POST /files/{id}/complete` verifies via HEAD and persists metadata.
- **Download:** presigned GET with short expiry; share links resolve to presigned GETs.
- **Versioning:** S3 bucket versioning ON; a new upload to the same key creates a new S3 version, recorded as a `FileVersion` item.
- Buckets are fully private — no public ACLs, no bucket policies granting anonymous read. Lifecycle: IA at 75 days, Glacier at 1 year (mirrors v1's documented lifecycle story).

## Consequences

- Backend handles only metadata: fast, cheap, small payloads.
- The 10MB limit is enforced by S3, not application code — one less trusted-client assumption.
- Complete-upload handshake required (client could abandon an upload; a later janitor or S3 event can reap orphans — v2 roadmap).
