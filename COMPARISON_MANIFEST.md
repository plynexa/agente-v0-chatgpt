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
| Context manager | FUNCTIONAL | Deterministic recent entity/project context and follow-up Test D pass |
| Versioned local API | FUNCTIONAL / UNTESTED_ON_REAL_DEVICE | Authenticated REST/WebSocket integration passes; Android LAN runtime awaits hardware |
| Memory and entity retrieval | FUNCTIONAL | Ranked local search; Yasmin restart test passed |
| Project relations | FUNCTIONAL | Airfry/Trendo restart test passed |
| Concurrent task engine | FUNCTIONAL | Tests E/F/G pass; priority workers, cancellation and events |
| Skills and permissions | FUNCTIONAL | Registry, built-ins and action permission policy compile/test |
| Local-first router | FUNCTIONAL | Deterministic intent test passed |
| MockAIProvider | MOCK | Functional deterministic provider; success/failure tests pass |
| External AI providers | NOT_IMPLEMENTED | Unconfigured contracts only; no keys required |
| Persistent reminders | FUNCTIONAL | Test H restart/one-shot trigger passed |
| Android reminder notification | UNTESTED_ON_REAL_DEVICE | Real notification adapter builds; device delivery not tested |
| Desktop local backup/restore | FUNCTIONAL | VACUUM snapshot, hash/integrity, atomic restore; Test I passed |
| Android local backup binding | UNTESTED_ON_REAL_DEVICE | Consistent snapshot, integrity, staged rollback restore and Core restart build/lint |
| Voice state/mute/barge-in | FUNCTIONAL_ARCHITECTURE | Test L and barge-in fake-engine test pass |
| Wake word/STT/TTS audio | PARTIAL / UNTESTED_ON_REAL_DEVICE | Offline Android engines and pipeline build; API 31+ recognizer/device validation required |
| Pairing/authenticated LAN | PARTIAL | Physical Wi-Fi REST pairing works; real WebSocket exposed an HTTP/WS scheme bug and the correction awaits retest |
| Windows token storage | UNTESTED_ON_WINDOWS | DPAPI implementation compiles; Linux fallback is development-only |
| Android build | SOURCE_BUILD_PASS | `assembleDebug` and `lintDebug` pass with locally installed official SDK |
| Desktop source build | SOURCE_BUILD_PASS | `:desktopApp:compileKotlinDesktop` passed |
| Windows `.exe` | BLOCKED | Linux host cannot create/validate Windows native package |
| Galaxy S10 test | UNTESTED_ON_REAL_DEVICE | No real device connected |

## Counts after Phase 11

- Automated tests authored: 20
- Automated tests executed: 20
- Automated tests passed: 20
- Required Tests A–L passed: 12
- Test M: Android/Desktop source pass; Windows `.exe` environment-limited
- External AI keys required to inspect/start source: 0
