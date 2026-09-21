# V0 Comparison Manifest

This file states only what is supported by current repository evidence.

Overall source V0 status: `V0_COMPLETE`. This does not upgrade any target-device
classification below; real Galaxy S10 and Windows execution remains pending.

| Capability | Classification | Evidence / limitation |
| --- | --- | --- |
| Independent project | FUNCTIONAL | New repository tree; no previous agent code imported |
| Architecture decision | FUNCTIONAL | `docs/ARCHITECTURE.md` |
| Shared Android/Desktop domain module | FUNCTIONAL | JVM target and generated SQLDelight sources compile |
| Required database table design | FUNCTIONAL | Normalized SQLDelight schema and runtime repositories pass persistence/restart tests |
| Versioned migrations | PARTIAL | Initial schema is version 1; no upgrade migration is needed/created yet |
| Event Bus | FUNCTIONAL | Coroutine implementation; unit test passed |
| Structured secret-safe logging | PARTIAL | Redaction implementation exists; unit test pending |
| Android host | UNTESTED_ON_REAL_DEVICE | Foreground service, SQLite core, API, reminders and build/lint pass; no Galaxy S10 run |
| Windows client | SOURCE_BUILD_PASS | Connected all-menu Compose panel; remote contract integration passes |
| Conversation history | FUNCTIONAL | File-backed SQLite restart test passed; originals retained |
| Unified conversation runtime | FUNCTIONAL | API chat and Android voice share one persistent context/memory/router pipeline; nine V0.2 tests pass |
| Context manager | FUNCTIONAL | Runtime resolves recent entity/project context and pronouns in follow-up tests |
| Versioned local API | FUNCTIONAL / UNTESTED_ON_REAL_DEVICE | Authenticated REST/WebSocket integration passes; Android LAN runtime awaits hardware |
| Memory and entity retrieval | FUNCTIONAL | Ranked local search; Yasmin restart test passed |
| Natural memory capture | FUNCTIONAL | Belinha/Safira declarative fact test and runtime retrieval pass |
| Memory CRUD | FUNCTIONAL | Authenticated update/delete API and connected Windows actions compile/test |
| Conversation lifecycle UI | FUNCTIONAL / UNTESTED_ON_WINDOWS | Create/select/rename/delete connected to host API; source build passes |
| Project relations | FUNCTIONAL | Airfry/Trendo restart test passed |
| Concurrent task engine | FUNCTIONAL | Tests E/F/G pass; priority workers, cancellation and events |
| Skills and permissions | FUNCTIONAL | Registry, built-ins and action permission policy compile/test |
| Local-first router | FUNCTIONAL | Deterministic intent test passed |
| MockAIProvider | MOCK | Functional deterministic provider; success/failure tests pass |
| External AI providers | NOT_IMPLEMENTED | Unconfigured contracts only; no keys required |
| Persistent reminders | FUNCTIONAL | Test H restart/one-shot trigger passed |
| Reminder management UI | FUNCTIONAL / UNTESTED_ON_WINDOWS | Manual date/time plus edit/delete API and cards compile/test |
| Android reminder notification | UNTESTED_ON_REAL_DEVICE | Real notification adapter builds; device delivery not tested |
| Desktop local backup/restore | FUNCTIONAL | VACUUM snapshot, hash/integrity, atomic restore; Test I passed |
| Android local backup binding | UNTESTED_ON_REAL_DEVICE | Consistent snapshot, integrity, staged rollback restore and Core restart build/lint |
| Voice state/mute/barge-in | FUNCTIONAL_ARCHITECTURE | Test L and barge-in fake-engine test pass |
| Wake word/STT/TTS audio | PARTIAL / UNTESTED_ON_REAL_DEVICE | Offline Android engines and pipeline build; API 31+ recognizer/device validation required |
| Pairing/authenticated LAN | PARTIAL | Physical Wi-Fi REST pairing worked; corrected WebSocket scheme passes integration tests and awaits V0.3 device retest |
| Automatic S10 discovery | UNTESTED_ON_REAL_NETWORK | Parallel local-subnet discovery compiles; physical IP-change test pending |
| Stable Windows device identity | FUNCTIONAL / UNTESTED_ON_WINDOWS | Persisted client UUID is sent during pairing; same id upserts rather than duplicates |
| Provider configuration | PARTIAL | CRUD/UI plus encrypted Android secret storage; execution is not connected |
| Cloud synchronization | PARTIAL | Provider-neutral push/pull coordinator test passes; no real cloud backend is configured |
| Windows token storage | UNTESTED_ON_WINDOWS | DPAPI implementation compiles; Linux fallback is development-only |
| Android build | SOURCE_BUILD_PASS | `assembleDebug` and `lintDebug` pass with locally installed official SDK |
| Desktop source build | SOURCE_BUILD_PASS | `:desktopApp:compileKotlinDesktop` passed |
| Windows `.exe` | BLOCKED | Linux host cannot create/validate Windows native package |
| Galaxy S10 test | UNTESTED_ON_REAL_DEVICE | No real device connected |

## Counts after V0.3

- Automated tests authored: 31
- Automated tests executed: 31
- Automated tests passed: 31
- Required Tests A–L passed: 12
- Test M: Android/Desktop source pass; Windows `.exe` environment-limited
- External AI keys required to inspect/start source: 0
