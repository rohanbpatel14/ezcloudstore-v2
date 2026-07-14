# ADR-0001: Spring Boot 3 on Lambda with SnapStart

Date: 2026-07-13 · Status: Accepted

## Context

v1 (2021) ran Spring Boot 2.5 as a WAR on Elastic Beanstalk EC2 instances — a standing cost incompatible with the free-tier constraint, and an outdated deployment story. The rebuild needs Java compute that (a) costs ~$0 at portfolio traffic, (b) keeps the Spring resume thread from the original, and (c) is recognizable to mainstream Java shops.

## Decision

Single Spring Boot 3.x (Java 21) application deployed as one Lambda function behind API Gateway HTTP API, with **SnapStart** enabled to eliminate the JVM cold-start penalty. The `aws-serverless-java-container-springboot3` adapter bridges API Gateway events to the servlet layer.

## Alternatives considered

- **Quarkus/GraalVM native** — faster cold starts, but loses Spring continuity, adds native-build CI complexity.
- **Plain handlers + Powertools** — lightest, but forfeits the framework story and reads less transferable.
- **ECS Fargate** — most production-representative, but a permanent ~$20+/mo bill violates the budget.

## Consequences

- Free tier covers 1M requests/month; scale-to-zero by construction.
- SnapStart requires published Lambda versions and has restore-time caveats (no ephemeral state captured at snapshot).
- The whole API is one deployable — consistent with the single-bounded-context domain.
