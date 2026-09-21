# Worker State

STATUS:
IN_PROGRESS

CURRENT_PHASE:
V0.3 — USABILITY, NATURAL MEMORY AND PORTABILITY / DEVICE VALIDATION

LAST_SUCCESSFUL_STEP:
Implemented and validated V0.3 natural memory capture, conversation/memory/
reminder CRUD, stable device pairing identity, automatic LAN discovery,
parallel/debounced Desktop loading, encrypted provider configuration and the
provider-neutral cloud sync foundation. All 31 shared tests pass; Desktop
compilation, Android debug APK assembly and Android lint pass.

CURRENT_PROBLEM:
The V0.3 APK and Windows client have not yet been executed together on the real
devices. Native EXE/MSI packaging requires Windows. Cloud sync has a tested
contract/coordinator but no deployed authenticated provider. The tested S10
previously reported that Android on-device speech recognition is unavailable.

NEXT_STEP:
On Windows run GERAR_EXE_WINDOWS.bat, install the new V0.3 APK with
INSTALAR_S10.bat, pair once, verify the Dashboard reports Core v0.3, then test
natural Belinha/Safira memory, memory edit/delete, new/renamed conversations,
manual reminder edit/delete, S10 auto-discovery and device deduplication.

FILES_CHANGED:
- Project root Gradle configuration and version catalog
- `shared`, `androidApp`, and `desktopApp` module builds and sources
- SQLDelight initial schema
- Event bus, logger, domain enums/status, and event bus test
- Mandatory control files and architecture documentation
- `.gitignore` and `.env.example`
- Phase 1 Agent Core, state, conversation/context managers and Ktor API
- Conversation/message SQLDelight queries and integration tests
- Phase 2 memory/entity/project repositories, managers, queries and tests
- Phase 3 durable concurrent Task Manager, repository, events and tests
- Phase 4 skills, permissions, router, providers and isolation tests
- Phase 5 durable reminders, scheduler, notification port and Test H
- Phase 6 validated local SQLite backup/restore and Test I
- Phase 7 voice contracts, state machine, mute/barge-in and Test L
- Phase 8 Android foreground host, notification, SQLite runtime, Ktor API,
  reminder notification, secure settings and offline speech/TTS adapters
- Phase 9 shared remote client and connected Compose Desktop panel
- Phase 10 one-time pairing, token authorization, LAN lifecycle and DPAPI store
- Phase 11 A–M evidence matrix, explicit Test J and Android backup binding
- Phase 12 release README, final classifications and checksummed artifacts
- V0.2 unified conversation runtime, runtime/API/voice integration, conversation
  lifecycle events, Windows Enter behavior and local development scripts
- V0.3 natural memory capture/retrieval, conversation/memory/reminder CRUD,
  stable device identity, LAN discovery, provider configuration, optimized
  Desktop refresh, cloud sync foundation and Windows EXE/MSI build scripts

TESTS_LAST_RUN:
- Local JDK 17 and official Android SDK: PASS
- `:shared:desktopTest`: PASS (31 tests, 0 failures, 0 skipped)
- `:desktopApp:compileKotlinDesktop`: PASS
- SQLDelight code generation: PASS
- `:androidApp:assembleDebug`: PASS
- `:androidApp:lintDebug`: PASS
- Host REST/WebSocket integration: PASS
- Desktop remote-client chat/memory integration: PASS
- Test K authenticated pairing/chat/events/reconnect: PASS
- Test J no-external-key local feature integration: PASS
- `:desktopApp:packageExe` / `packageMsi`: configured; target build pending on Windows
- V0.3 APK SHA-256:
  `4621a2660ff16a85bbc1039ea7ebf96d90a458c423ea766cc89a8decfa3f7737`

KNOWN_BLOCKERS:
- V0.3 device flow is `UNTESTED_ON_REAL_DEVICE` in this build environment.
- Android on-device wake/STT requires API 31+ and an installed offline language
  recognizer; the tested S10 previously reported it unavailable.
- Windows `.exe` packaging cannot be validated on this Linux environment.
- Android backup snapshot/restore source is `UNTESTED_ON_REAL_DEVICE`.
- Real cloud database sync is `PARTIAL`: provider selection, deployment,
  authentication, conflict resolution and Android scheduling remain pending.
