# Worker State

STATUS:
IN_PROGRESS

CURRENT_PHASE:
POST-V0 — REAL DEVICE VALIDATION

LAST_SUCCESSFUL_STEP:
Installed the V0 APK on a Galaxy S10 and paired the Windows client over real
Wi-Fi. Authenticated REST state recovery works and reports the S10 online.

CURRENT_PROBLEM:
Real CIO WebSocket remained in RECONNECTING because AgentRemoteClient passed an
HTTP URL to the WebSocket client. The S10 also reports that Android on-device
speech recognition is unavailable; this voice limitation is not yet resolved.

NEXT_STEP:
Validate the HTTP-to-WS URL correction with automated tests, issue a new source
test package, run it on Windows, and confirm the live-event indicator changes
from RECONNECTING to CONNECTED before investigating the S10 speech service.

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

TESTS_LAST_RUN:
- Environment probe: PASS (Java 17 and Git available)
- `:shared:desktopTest`: PASS (20 tests, 0 failures, 0 skipped)
- `:desktopApp:compileKotlinDesktop`: PASS
- SQLDelight code generation: PASS
- `:androidApp:assembleDebug`: PASS
- `:androidApp:lintDebug`: PASS
- Host REST/WebSocket integration: PASS
- Desktop remote-client chat/memory integration: PASS
- Test K authenticated pairing/chat/events/reconnect: PASS
- Test J no-external-key local feature integration: PASS
- `:desktopApp:createDistributable`: PASS (Linux application image)
- `:desktopApp:packageExe`: SKIPPED (Linux host; no `.exe` claimed)

KNOWN_BLOCKERS:
- No Galaxy S10 real-device validation is possible here; Android runtime and
  acoustic behavior are `UNTESTED_ON_REAL_DEVICE`.
- Android on-device wake/STT requires API 31+ and an installed offline language
  recognizer; target-device availability is unconfirmed.
- Windows `.exe` packaging cannot be validated on this Linux environment.
- Android backup snapshot/restore source is `UNTESTED_ON_REAL_DEVICE`.
