# fp-prosesstask

Framework for persistent, fault-tolerant async processing across a cluster of pods.

## Shared context

- Source of truth for shared domain, architecture, and conventions: `navikt/fp-context`
- Copilot Space: `navikt/TeamForeldrepenger`
- Consumer view of team libraries: `fp-context/architecture/team-libraries.md`

## What it provides

| Capability | Description |
|---|---|
| Persistent tasks | Tasks are stored in the database and survive restarts |
| Distributed execution | Pollers across pods pick up ready tasks |
| Sequential and parallel flows | Tasks can depend on other tasks |
| Retry semantics | Error handling is configurable per task |
| Scheduled jobs | Cron-like recurring tasks |
| Fail-fast coding | Throw on bad data; the framework persists for retry after the fix |

## Module layout

| Module | Purpose |
|---|---|
| `task` | Core framework, polling, and dispatch |
| `rest` | REST endpoints for monitoring and administration |
| `kontekst` | Tie-in with `fp-felles` logging and context |

## Usage patterns

| Pattern | Use case                                                                                              |
|---|-------------------------------------------------------------------------------------------------------|
| Outbox | Isolate calls to REST, Kafka, or MQ to separate transactions or failure-units; beware of idempontency |
| Inbox | Take ownership of inbound messages before further processing; store message and prosesstask           |
| Saga | Orchestrated multi-step transactions                                                                  |
| Scheduled | Cron-like recurring jobs                                                                              |

## Entry points

- `ProsessTaskTjeneste` for programatically creating and managing tasks
- `ProsessTaskRestTjeneste` for manual administration; consumers wrap in separate RS application under context-path/forvaltning for use in `fp-swagger` 
- `TaskManager` - the main polling and dispatch loop; consumers may configre threads and need to start this explicitly as any `Controllable`

## When changing this repo

- Public task API changes are breaking and affect many consumers.
- Database schema changes need migration scripts compatible with all consumer apps.
- Polling and dispatch logic affects throughput across the platform.
- Preserve fail-fast and retry semantics; consumers depend on them.

## Release and use

SemVer release; version not included in `fp-bom`; imported directly by many repos in the foreldrepenger ecosystem
