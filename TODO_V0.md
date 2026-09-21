# TODO V0

Statuses: `[ ]` pending, `[~]` partial/blocked, `[x]` evidenced complete.

## Phase 0 — Foundation

- [x] Choose and document one stack.
- [x] Create independent project/module structure.
- [x] Add versioned SQLDelight database source schema.
- [x] Add Event Bus contract and in-memory implementation.
- [x] Add secret-redacting structured logger.
- [x] Add minimum Android and Desktop source shells.
- [x] Generate Gradle wrapper JAR and compile shared/Desktop modules.
- [x] Run foundation unit tests.
- [x] Compile and lint Android host with a locally installed official SDK.
- [ ] Add explicit numbered SQLDelight migration after the first schema change.

## Phase 1 — Agent Core

- [x] Root supervised Agent Core lifecycle and state store.
- [x] Conversation Manager and durable message repository.
- [x] Deterministic recent Context Manager foundation.
- [x] Versioned authenticated REST API and WebSocket event stream.
- [x] Persistence restart test (Test A).

## Phase 2 — Memory & Projects

- [x] Memory/entity/project repositories and ranked text search.
- [x] Entity/project relation persistence and retrieval.
- [x] Yasmin persistence test (Test B).
- [x] Trendo/Airfry relation test (Test C).
- [x] Follow-up context test (Test D).

## Phase 3 — Task Engine

- [x] Durable priority queue, supervised workers and cancellation.
- [~] Restart recovery contract pending registered handler restoration.
- [x] Genuine lifecycle/progress events.
- [x] Multitask, failure isolation, and event tests (E, F, G).

## Phase 4 — Skills & Router

- [x] Skill/permission/provider contracts and registries.
- [x] Heuristic Action Router.
- [x] MemorySkill, ReminderSkill, SystemStatusSkill and EchoSkill.
- [x] Functional MockAIProvider and future provider contracts.
- [x] Local-without-key components covered by explicit Test J.

## Phase 5 — Reminders

- [x] Durable repository, scheduler and events.
- [x] Android notification adapter interface and platform implementation.
- [x] Restart persistence test (Test H).

## Phase 6 — Backup

- [x] BackupProvider and desktop LocalBackupProvider.
- [x] SQLite online snapshot, validation, safe restore and audit.
- [x] Backup/restore integration test (Test I).
- [x] Android consistent snapshot/validation/staged rollback restore binding.

## Phase 7 — Voice Core

- [x] WakeWordEngine, SpeechToTextEngine, TextToSpeechEngine contracts.
- [x] Voice state machine, standby/mute and barge-in architecture.
- [x] State transition test (Test L).
- [x] Mark actual wake/STT/TTS as UNTESTED_ON_REAL_DEVICE.

## Phase 8 — Android Host

- [x] Foreground service and persistent, accurate notification.
- [x] Host Agent Core, SQLite, Ktor API/WebSocket and reminder scheduler.
- [x] Android secure token/provider secret storage.
- [x] Android source/build and lint test (part of Test M).
- [~] Offline wake/STT/TTS implemented; real Galaxy S10 validation pending.
- [x] Android consistent snapshot/validation/staged rollback restore binding.
- [~] Android backup runtime remains untested on a Galaxy S10.

## Phase 9 — Windows Client

- [x] Connected Dashboard and required menu.
- [x] Chat/memory/project/task/reminder/skill/log/device/model/settings screens.
- [x] Remote typed API/WebSocket state and reconnect handling.
- [x] Desktop source build; Windows `.exe` remains separately environment-limited.

## Phase 10 — Pairing & LAN

- [x] Localhost-default configuration and explicit temporary/persistent LAN lifecycle.
- [x] Expiring one-time pairing, bounded attempts and peppered token hashes.
- [x] Authenticated REST/WebSocket, Windows DPAPI storage and reconnect Test K.
- [~] Real Galaxy S10 ↔ Windows Wi-Fi validation remains pending hardware.

## Phase 11 — Integration Tests

- [x] Execute Tests A–M and retain evidence in `docs/TEST_MATRIX.md`.
- [x] Add explicit Test J without any external provider/key.
- [x] Resolve all environment-independent build/test failures.
- [~] Windows `.exe` and Galaxy S10 tests remain environment limitations.

## Phase 12 — V0 Release

- [x] Complete README and all control documents.
- [x] Produce validated Android debug APK artifact.
- [x] Produce Desktop Linux distribution; classify `.exe` as Windows-only.
- [x] Produce checksummed source release artifact.
- [x] Mark source-build V0 `V0_COMPLETE` after required automated evidence.

## Post-V0 hardware validation

- [~] Install and exercise foreground service, reminders, backup and voice on Galaxy S10.
- [~] Physical S10 ↔ Windows REST pairing works; validate the corrected real WebSocket URL.
- [~] Build and validate native Windows `.exe` and DPAPI token persistence.
- [~] Resolve unavailable Android on-device speech service on the tested Galaxy S10.

## V0.2 — Unified Conversation Runtime

- [x] Add one authoritative runtime for chat and transcribed voice input.
- [x] Integrate conversation, context, memory, projects, router, tasks and reminders.
- [x] Persist user/reply pairs in the same conversation with route/context metadata.
- [x] Add useful local answers and explicit missing-provider responses.
- [x] Route Android voice transcripts and Windows/API chat through the runtime.
- [x] Add Enter to send and Shift+Enter for a new line on Windows.
- [x] Add Windows launcher, test, Android build and ADB install scripts.
- [x] Add eight required scenarios plus truthful event-lifecycle coverage.
- [x] Build/lint the V0.2 APK and compile the Desktop client.
- [~] Install V0.2 on the S10 and validate the full physical conversation flow.
- [~] Build and validate the native Windows `.exe` on Windows.

## V0.3 — Usability, natural memory and portability

- [x] Capture ordinary first-person/declarative facts from chat into memory.
- [x] Improve Portuguese memory retrieval and perspective-aware local answers.
- [x] Add memory edit/delete API and Windows card actions.
- [x] Add conversation create/select/rename/delete in the Windows client.
- [x] Add reminder date/time creation, edit and delete controls.
- [x] Give each Windows installation a persistent device id and prevent duplicate
  rows when the same client is paired again.
- [x] Add device revocation and automatic S10 discovery on the local subnet.
- [x] Load independent dashboard resources concurrently and debounce event refresh.
- [x] Add encrypted Android provider configuration and Windows configuration UI.
- [x] Configure Compose Desktop 0.3.0 EXE/MSI tasks and one-click batch scripts.
- [x] Add provider-neutral cloud sync contracts/coordinator and automated test.
- [~] Connect saved provider configurations to real AI inference.
- [~] Add a safe install/configure lifecycle for third-party skills; V0.3 keeps
  the four executable built-in skills instead of creating inert fake plugins.
- [~] Deploy and bind a real authenticated cloud sync provider; SQLite remains
  authoritative until conflict/retry/security behavior is validated.
- [~] Generate and validate native EXE/MSI on a Windows host.
- [~] Install V0.3 on the Galaxy S10 and validate the physical end-to-end flow.
