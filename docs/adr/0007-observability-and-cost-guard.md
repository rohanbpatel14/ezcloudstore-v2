# ADR-0007: Free-tier observability and a cost tripwire

Date: 2026-07-14 · Status: Accepted

## Context

The original project's documentation claimed CloudWatch monitoring and SNS
alerting, but none of it was in the codebase. The rebuild should actually
have operational visibility — and, given the hard "$0/month" constraint, a
guard that catches a runaway resource before it bills real money. Everything
chosen here stays inside the AWS always-free / free-tier allowances.

## Decision

A dedicated `ObservabilityStack`:

- **SNS topic** (`ezcloudstore-alerts`) with an email subscription — the alert channel.
- **CloudWatch alarms**, each wired to the topic:
  - Lambda `Errors ≥ 1` over 5 min
  - Lambda `Throttles ≥ 1` over 5 min
  - API Gateway `5xx ≥ 1` over 5 min
- **CloudWatch dashboard** (`EzCloudStore`): Lambda invocations/errors/throttles + p95 duration, API count/4xx/5xx + p95 latency.
- **AWS Budgets** monthly cost budget (default $1) with email alerts at 80% and 100% — the free-tier tripwire.
- **X-Ray active tracing** on the Lambda (free tier: 100k traces/mo) for per-request traces.

Free-tier math: CloudWatch gives 10 alarms + 3 dashboards free; SNS 1k email
notifications free; AWS Budgets first 2 budgets free; X-Ray 100k traces free.
This stack uses a fraction of each.

## Alternatives / deliberately excluded

- **CloudWatch Synthetics canary** — richer uptime probing, but not free. The
  GitHub Actions post-deploy smoke test covers the same ground at $0.
- **WAF / Cognito advanced security** — both bill per request / per MAU. Out.

## Consequences

- Real alerting the original only claimed, plus a spend circuit-breaker.
- SNS email + both budget emails require a one-time confirmation click.
- Alarms need ~a data point to leave INSUFFICIENT_DATA; `treatMissingData=NOT_BREACHING` keeps a quiet system from false-alarming.
