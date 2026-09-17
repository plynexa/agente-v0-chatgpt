# Agent V0 ChatGPT — Master Context

## Objective

Build a local-first personal agent whose authoritative host is a Samsung Galaxy
S10 and whose Windows application is a remote panel/client. The agent owns one
memory, one task queue, and multiple interfaces. Optional external AIs are tools;
they must never be hard runtime dependencies.

## Architecture and stack

The fixed V0 stack is Kotlin Multiplatform + Compose Multiplatform + Coroutines/
Flow + SQLDelight/SQLite + Ktor. See `docs/ARCHITECTURE.md`.

## Modules

- `shared`: reusable Agent Core, persistence schema, events, managers, router,
  skill/provider contracts, API models and tests.
- `androidApp`: authoritative Android host: foreground service, persistent
  notification, SQLite Agent Core, local API, reminder/voice adapters and
  encrypted preference storage.
- `desktopApp`: Windows remote dashboard/client. It must not become a competing
  authoritative Agent Core.

## Rules that cannot be broken

1. This project is independent; do not import code from any competing/previous
   implementation.
2. Android/S10 is the production source of truth. Windows is a client.
3. Preserve every original message; never replace full history with summaries.
4. Local features work without external AI keys.
5. Long tasks cannot block chat or the main UI thread.
6. Do not claim simulated, mocked, partial, or untested behavior as functional.
7. Do not invent provider progress.
8. Do not log or commit tokens, passwords, API keys, or raw device credentials.
9. LAN is off and localhost-bound by default; administrative access requires
   pairing and authorization.
10. Every phase ends with tests, documentation, state update, and Git checkpoint
    when Git is available.

## Important decisions

- A local LLM is optional behind `LocalModelProvider`; V0 boot cannot require it.
- SQLite is the durable host database. JSON is only flexible metadata.
- Voice, chat, and remote client messages enter the same conversation history.
- Backup and device sync are distinct. V0 implements local backup and LAN access;
  cloud sync remains future work.
- Wake word, STT, and TTS stay behind replaceable contracts.

## Critical files

- `WORKER_STATE.md`: authoritative continuation point.
- `TODO_V0.md`: incomplete work by phase.
- `CHANGELOG.md`: chronological changes.
- `COMPARISON_MANIFEST.md`: evidence-based feature classification.
- `docs/ARCHITECTURE.md`: architecture decision and boundaries.
- `shared/src/commonMain/sqldelight/.../Agent.sq`: initial database schema.
- `.env.example` and `.gitignore`: configuration and secret safety.

## Implemented checkpoints

- Phase 0: shared/desktop build, schema codegen, Event Bus and UI shells.
- Phase 1: supervised Agent Core/state, conversation persistence and history
  search, recent context snapshot, and Ktor health/status/chat endpoints.
- Phase 2: typed memory/entity/project repositories, relations and deterministic
  ranked local retrieval; Tests B/C/D pass.
- Phase 3: durable concurrent priority Task Manager, truthful events/progress,
  cancellation and failure isolation; Tests E/F/G pass.
- Phase 4: local-first router, skill/permission/provider contracts, built-in
  skills and functional MockAIProvider; external providers remain unconfigured.
- Phase 5: durable restart-safe reminders, scheduler/events and notification
  adapter contract; Test H passes.
- Phase 6: validated desktop SQLite snapshot/restore with quiescence, audit and
  rollback; Test I passes. The later Phase 11 Android binding compiles and
  passes lint but remains untested on a real device.
- Phase 7: voice engine contracts, state machine, mute and barge-in architecture;
  Test L passes, real-device audio remains untested.
- Phase 8: Android foreground host, persistent notification, SQLite runtime,
  REST/WebSocket API, reminder notifications, encrypted settings and on-device
  speech/TTS implementation. Android debug build and lint pass; Galaxy S10
  behavior remains untested on real hardware.
- Phase 9: connected Compose Desktop panel backed by shared typed REST/WebSocket
  client with reconnect, live chat/state/events and all required menu views.
  Desktop source compiles; native Windows packaging remains environment-limited.
- Phase 10: localhost-default Android API, temporary one-time-code LAN pairing,
  server-secret-peppered token hashes, authenticated REST/WebSocket routes,
  device revocation and Windows DPAPI token storage. Test K passes in-process;
  real S10/Wi-Fi validation remains pending.
- Phase 11: complete automated evidence matrix A–M with 20 passing tests,
  explicit no-external-key Test J, Android backup binding, APK/lint pass and
  Desktop Linux distribution. Windows `.exe` and real-device tests remain
  correctly environment-limited.
- Phase 12: source V0 release documentation and artifacts completed. The
  repository is `V0_COMPLETE` for tested source scope; target-device limitations
  remain classifications, not hidden claims.

## Continuation protocol

Read this file, then `WORKER_STATE.md`, `TODO_V0.md`, and the latest
`CHANGELOG.md` entries. Start only the exact `NEXT_STEP`. Finish the smallest
safe unit, compile/test, update control files, and commit before beginning
another unit.
