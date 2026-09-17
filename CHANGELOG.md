# Changelog

## 2026-09-16 — Real-device validation follow-up

- Confirmed authenticated REST pairing between the Galaxy S10 host and Windows
  client on a physical Wi-Fi network.
- Found that the real CIO WebSocket client received an `http://` event URL,
  leaving live events in `RECONNECTING` even while REST showed the S10 online.
- Changed the remote event URL to explicit `ws://`/`wss://` according to the
  configured HTTP endpoint.
- Confirmed the tested S10 currently reports Android on-device recognition as
  unavailable; voice remains unresolved rather than being marked functional.

All notable changes are documented chronologically. Feature classifications are
evidence-based and may be downgraded when verification is unavailable.

## 2026-09-16 — Phase 0 started

### Added

- Independent `agent-v0-chatgpt` Kotlin Multiplatform project.
- Shared module for Android/JVM, Android application, and Compose Desktop client.
- Pinned dependency version catalog and Gradle wrapper configuration.
- Complete initial normalized SQLite/SQLDelight schema for required V0 domains.
- Coroutine Flow-based Event Bus and initial event taxonomy.
- Structured logger with sensitive-field redaction.
- Common state, message, memory, task, voice, permission and status models.
- Minimal dark Android host screen and futuristic Desktop dashboard shell.
- Secret-safe `.env.example` and repository exclusions.
- Architecture record and mandatory continuity/control documents.
- Event Bus unit test source.

### Validation

- Java 17 and Git environment checks passed; official Gradle 8.10.2 wrapper JAR
  was added.
- `:shared:desktopTest` passed after fixing the Event Bus test subscription race.
- `:desktopApp:compileKotlinDesktop` passed after correcting its source-set path.
- SQLDelight generation and shared compilation passed after applying the Android
  library plugin and using portable integer flags in the schema.
- Android build additionally blocked by absent Android SDK.

### Classification

- Phase 0 shared/Desktop foundation: `FUNCTIONAL` with build/test evidence.
- Android/Windows applications: `PARTIAL` shells, not functioning Agent clients.

## 2026-09-16 — Phase 1 completed

### Added

- Supervised Agent Core lifecycle and observable Agent state store.
- SQLDelight conversation repository with transactional message insertion,
  complete-history retrieval, recent context and literal history search.
- Conversation Manager that persists source/device metadata and emits genuine
  `MESSAGE_RECEIVED` / `MESSAGE_CREATED` events.
- Deterministic recent Context Manager foundation.
- Versioned Ktor endpoints for health, status and chat.
- SQLite close/reopen persistence integration test (Test A).
- Ktor integration test proving API requests reach the shared Agent Core.

### Validation

- `:shared:desktopTest`: 3 tests passed, 0 failures.
- `:desktopApp:compileKotlinDesktop`: passed.
- Test A: passed with a real file-backed SQLite database.

## 2026-09-16 — Phase 2 completed

### Added

- Typed memory repository/manager for working, episodic, semantic, project,
  preference, decision and lesson memories.
- Local ranked text retrieval using phrase match, token overlap and importance.
- Persistent named entities, aliases and typed entity relations.
- Persistent projects, parent links and typed project relations.
- Tests B, C and D covering Yasmin, Airfry/Trendo and recent follow-up context.

### Validation

- `:shared:desktopTest`: 6 tests passed, 0 failures.
- Yasmin semantic memory/entity relation survived database restart.
- `Airfry --belongs_to--> Trendo` survived database restart.
- Recent context deterministically retained `Airfry` across a pronoun follow-up.

## 2026-09-16 — Phase 3 completed

### Added

- Durable task repository with queue/running/waiting/terminal states and events.
- Supervised multi-worker priority Task Manager with cancellation.
- Explicit progress evidence requirement; no synthetic percentages.
- Clean Task Manager lifecycle shutdown.
- Tests E, F and G for concurrent chat, failure isolation and event order.

### Validation

- `:shared:desktopTest`: 9 tests passed, 0 failures.
- A gated long task remained active while a new chat message persisted.
- A forced task failure did not stop a healthy concurrent task.
- Persisted lifecycle was exactly `CREATED → STARTED → COMPLETED`.

## 2026-09-16 — Phase 4 completed

### Added

- Skill definitions, registry and action-specific permission checks.
- SAFE / CONFIRMATION_REQUIRED / RESTRICTED permission policy.
- Local-first heuristic Action Router with required route categories.
- Echo, System Status, Memory and Reminder skill implementations/contracts.
- Common AIProvider contract, Provider Executor and functional MockAIProvider.
- Prepared unconfigured OpenAI, Anthropic, Gemini, local-model and image providers.

### Validation

- `:shared:desktopTest`: 12 tests passed, 0 failures.
- Router chooses local status/memory/reminder paths without external AI.
- Forced MockAIProvider failure emits a real error event and EchoSkill still works.
- MockAIProvider can be selected and executed without any API key.

## 2026-09-16 — Phase 5 completed

### Added

- Durable reminder repository and scheduled/due queries.
- Reminder Manager with scheduler loop, one-shot trigger semantics and events.
- Platform notification adapter contract and no-op development adapter.
- Test H with file-backed SQLite restart and trigger verification.

### Validation

- `:shared:desktopTest`: 13 tests passed, 0 failures.
- Reminder persisted across restart, notified once and moved to `TRIGGERED`.

## 2026-09-16 — Phase 6 completed

### Added

- BackupProvider plus future R2/Google Drive contracts (not implemented).
- Desktop LocalBackupProvider using SQLite `VACUUM INTO` rather than unsafe copy.
- SHA-256, file size and `PRAGMA integrity_check` validation.
- Database lifecycle quiescence and staged restore with atomic move/rollback.
- Backup audit rows and real backup lifecycle events.
- Test I covering create, mutate, restore and data verification.

### Validation

- `:shared:desktopTest`: 14 tests passed, 0 failures.
- Test I restored the snapshot and excluded post-snapshot data.

## 2026-09-16 — Phase 7 completed

### Added

- Replaceable WakeWordEngine, SpeechToTextEngine and TextToSpeechEngine contracts.
- Offline voice model requirement descriptor.
- Voice state machine for standby/listening/thinking/processing/speaking/muted/error.
- Microphone mute that stops wake/STT and requires explicit unmute.
- Barge-in path that stops TTS and returns to listening.
- Test L plus explicit barge-in test.

### Validation

- `:shared:desktopTest`: 16 tests passed, 0 failures.
- Voice engine behavior uses fakes only and remains `UNTESTED_ON_REAL_DEVICE`.

## 2026-09-16 — Phase 8 completed

### Added

- Android foreground service with persistent, state-accurate notification,
  `START_STICKY` lifecycle and clean shutdown.
- Android SQLDelight Agent Core runtime independent of the activity.
- Expanded REST API plus live WebSocket event stream for conversations,
  memories, tasks and reminders.
- Real Android reminder notifications and encrypted local setting storage.
- Offline-only Android `SpeechRecognizer` adapter, Android TTS adapter, wake
  acknowledgement and continuous-conversation pipeline.
- Host controls for start/stop, voice, hard microphone mute and battery settings.
- Android host operations/classification guide.

### Validation

- Installed the official Android command-line SDK, platform 35, build tools and
  a local JDK 17 without modifying system packages.
- `:androidApp:assembleDebug`: passed; debug APK produced.
- `:androidApp:lintDebug`: passed without a lint baseline.
- `:shared:desktopTest`: 17 tests passed, including REST/WebSocket host event
  integration.
- Actual service/notification/acoustic behavior remains
  `UNTESTED_ON_REAL_DEVICE` because no Galaxy S10 is attached.

## 2026-09-16 — Phase 9 completed

### Added

- Shared typed `AgentRemoteClient` for host REST operations and reconnecting
  WebSocket events.
- Host chat now persists both the Windows user message and a deterministic local
  agent reply without any external AI key.
- Host read endpoints for projects, installed skills and device capabilities.
- Connected dark Compose Desktop panel with every required V0 menu and live
  Dashboard, Chat, Memory, Projects, Skills, Tasks, Reminders, History, Devices,
  Models, Logs and Settings views.
- Persisted host address, explicit reconnect and authoritative state refresh
  after live events.

### Validation

- `:shared:desktopTest`: 18 tests passed, including a Desktop remote-client
  contract test for chat reply and memory retrieval.
- `:desktopApp:compileKotlinDesktop`: passed.
- `:androidApp:assembleDebug`: still passes after API expansion.
- Windows `.exe` is not claimed; packaging requires a Windows environment.

## 2026-09-16 — Phase 10 completed

### Added

- Device repository over the existing normalized SQLite `devices` table.
- Cryptographically random six-digit pairing code and 256-bit device token
  generation, with five-minute expiry, single use and five-attempt limit.
- Server-secret-peppered SHA-256 token storage; plaintext tokens are returned
  once and never stored or logged by the host.
- Bearer authentication around every administrative REST and WebSocket route;
  only health and pairing endpoints remain public.
- Temporary LAN binding during pairing, persistent LAN only after success,
  automatic localhost fallback on expiry and explicit disable action.
- Android pairing code/address UI and persistent-notification action.
- Windows pairing controls and DPAPI-backed token store.
- Device listing, last-seen updates and authenticated revocation endpoint.

### Validation

- Test K passes: unauthorized access is rejected, one-time pairing succeeds,
  stored credential differs from plaintext, authenticated chat/events work and
  a new client recovers the existing conversation.
- `:shared:desktopTest`: 19 tests passed.
- `:desktopApp:compileKotlinDesktop`: passed.
- `:androidApp:assembleDebug` and `:androidApp:lintDebug`: passed.
- Real LAN and Windows DPAPI execution remain untested on their target hardware.

## 2026-09-16 — Phase 11 completed

### Added

- Explicit Test J proving chat, status, memory, tasks, reminders and backup work
  without external providers or API keys.
- Android local backup provider using WAL checkpoint plus SQLite `VACUUM INTO`,
  size/hash/integrity validation, no-backup storage and audit/events.
- Android staged restore with durable copy, integrity checks, rollback on failure
  and controlled Agent Core restart; UI restore requires confirmation.
- Authenticated backup list/create/restore API endpoints.
- Complete A–M evidence matrix in `docs/TEST_MATRIX.md`.

### Validation

- `:shared:desktopTest`: 20 passed, 0 failed, 0 skipped.
- `:androidApp:assembleDebug` and `:androidApp:lintDebug`: passed.
- `:desktopApp:compileKotlinDesktop`: passed.
- `:desktopApp:createDistributable`: passed and produced a Linux app image.
- `:desktopApp:packageExe`: explicitly attempted and `SKIPPED` on Linux;
  classification is `WINDOWS_EXE_NOT_BUILT_ENVIRONMENT_LIMITATION`.
- Android backup runtime and all Galaxy S10 behavior remain untested on hardware.

## 2026-09-16 — Phase 12 V0 release completed

### Added

- Complete root README covering architecture, build, APK install, foreground
  host, pairing, Windows client, permissions, database, backup, voice, tests,
  security and limitations.
- Updated architecture and all mandatory continuity/classification documents.
- Release notes, SHA-256 manifest, Android debug APK and source archive.

### Final validation

- 20 automated tests passed; 0 failed; 0 skipped.
- Android APK assembly and lint passed.
- Desktop source compilation and Linux application distribution passed.
- Windows `.exe` task was attempted and skipped on Linux; no `.exe` claim.
- Source-build V0 status is `V0_COMPLETE` with real-device features still
  explicitly `UNTESTED_ON_REAL_DEVICE` where applicable.
