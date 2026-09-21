# Cloud Sync — V0.3 foundation

## Intended data model

The Galaxy S10 keeps a complete SQLite database so the agent remains usable
offline. A configured cloud service will receive incremental changes and allow
another authorized device to restore or synchronize the same account. Local
backup remains a separate recovery feature.

```text
S10 SQLite <-> CloudSyncCoordinator <-> CloudSyncProvider <-> cloud database
```

## Implemented in V0.3

- Provider-neutral `CloudSyncProvider` push/pull contract.
- Typed snapshot for conversations, messages, memories, projects and reminders.
- `CloudSyncCoordinator` with cursor advancement and real started/completed/
  failed events.
- Automated fake-provider test proving push, pull, apply and cursor behavior.

Classification: `PARTIAL`. This is a tested synchronization foundation, not a
deployed cloud database.

## Still required before cloud can be authoritative

- Select and configure a real cloud database/service.
- Authenticate the user and each device independently.
- Implement encrypted transport, an outbox, retries and conflict resolution.
- Persist the sync cursor and deletion tombstones.
- Bind the coordinator to Android scheduling and network constraints.
- Perform migration/recovery and multi-device tests.

No endpoint, API key or cloud credential is hard-coded. Until those items are
implemented and validated, SQLite on the S10 remains the authoritative runtime
database and the agent continues to work offline.
