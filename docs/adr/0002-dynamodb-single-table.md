# ADR-0002: DynamoDB single-table design replaces MySQL RDS

Date: 2026-07-13 · Status: Accepted

## Context

v1 used MySQL on RDS (two tables: User, Files). RDS has no meaningful always-free tier; the access patterns are simple key-based lookups (files by owner, versions by file, share link by token, all files for admin) with no relational joins that matter.

## Decision

One DynamoDB table `ezcloudstore` (on-demand billing, 25GB always-free):

| Entity | PK | SK | Notes |
|---|---|---|---|
| File metadata | `USER#<sub>` | `FILE#<id>` | owner's file listing |
| File version | `FILE#<id>` | `VERSION#<ts>` | version history |
| Share link | `SHARE#<token>` | `META` | TTL attribute = expiry |
| Admin listing | GSI1: `ENTITY#FILE` | `<createdAt>` | list-all for admin |

User profiles live in Cognito, not DynamoDB — no duplicated identity store.

## Consequences

- $0 at portfolio scale; no connection pooling, no VPC, no maintenance windows.
- Access patterns must be known up front (they are — the domain is stable since 2021).
- Interview talking point: relational → single-table modeling rationale, TTL-based expiry for share links.
