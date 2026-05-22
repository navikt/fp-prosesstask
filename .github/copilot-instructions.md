# fp-prosesstask

Framework for persistent, fault-tolerant async processing across a cluster
of pods. Used by all Team Foreldrepenger backend apps for background work.

## Context

- [fp-context](https://github.com/navikt/fp-context) — team domain,
  architecture, conventions. Source of truth.
- Consumer view of the patterns this enables:
  [`architecture/team-libraries.md`](https://github.com/navikt/fp-context/blob/main/architecture/team-libraries.md).
- Copilot Space: navikt / **TeamForeldrepenger**.

## What it provides

| Capability | Description |
|------------|-------------|
| Persistent tasks | Tasks stored in DB; survive restarts |
| Distributed execution | Pollers across pods pick up ready tasks |
| Sequential / parallel | Tasks can depend on other tasks |
| Retry semantics | Configurable per-task error handling |
| Scheduled jobs | Cron-like recurring tasks |
| Fail-fast coding | Throw on bad data; framework persists for retry after fix |

## Module layout

| Module | Purpose                                       |
|--------|-----------------------------------------------|
| `task` | Core task framework, polling, dispatch        |
| `rest` | REST endpoints for monitoring/admin |
| `kontekst` | Tie-in with fp-felles / log + kontekst        |

## Usage patterns (in consumer apps)

| Pattern | Use case |
|---------|----------|
| Outbox | Calls to REST/Kafka/MQ outside DB transaction; target must be idempotent |
| Inbox | Take ownership of inbound messages before further processing |
| Saga | Orchestrated multi-step transactions |
| Scheduled | Cron-like recurring jobs |

## When changing this repo

- Public task API changes are breaking — bump major, coordinate with consumers
- DB schema changes need migration scripts compatible with all consumer apps
- Polling/dispatch logic affects throughput everywhere — benchmark
- Preserve fail-fast + retry semantics; consumers depend on it

## Release

SemVer; release artifact published to consumer repos via fp-bom + Dependabot.

## Tech

Java 25, Jakarta EE 11, JPA/Hibernate, Maven. Versions pinned via `fp-bom`.
