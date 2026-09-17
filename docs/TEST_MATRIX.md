# V0 Test Matrix — Phase 11

Evidence captured on 2026-09-16 in the Linux build environment.

| Test | Result | Evidence |
| --- | --- | --- |
| A — Persistence | PASS | `ConversationPersistenceTest`: file SQLite close/reopen preserves original messages |
| B — Yasmin | PASS | `MemoryPersistenceTest`: semantic memory survives restart and local search |
| C — Projects | PASS | `ProjectPersistenceTest`: `Airfry --belongs_to--> Trendo` survives restart |
| D — Context | PASS | `ContextManagerTest`: deterministic recent context retains Airfry in follow-up |
| E — Multitask | PASS | `TaskManagerTest.longTaskDoesNotBlockConversation` |
| F — Failure isolation | PASS | Forced provider/task failure does not stop the next healthy task |
| G — Events | PASS | Persisted lifecycle is exactly created, started, completed |
| H — Reminder | PASS | `ReminderPersistenceTest`: survives restart and triggers once |
| I — Backup | PASS | Desktop SQLite snapshot validates, mutates, restores and removes post-snapshot data |
| J — No external AI | PASS | `NoExternalAiIntegrationTest`: chat, memory, task, reminder, status and backup work with no provider/key |
| K — Windows/S10 contract | PASS | Pairing test covers 401, token hash, authenticated chat, WebSocket event and reconnect state |
| L — Voice states | PASS | Required state path, mute and barge-in unit tests pass |
| M — Builds | PARTIAL PASS | Android APK/lint and Desktop source/Linux app distribution pass; Windows `.exe` task is skipped on Linux |

## Commands and totals

```shell
./gradlew :shared:desktopTest
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
./gradlew :desktopApp:createDistributable
./gradlew :desktopApp:packageExe
```

- Automated tests: 20 passed, 0 failed, 0 skipped.
- Android: `SOURCE_BUILD_PASS`; debug APK produced.
- Desktop: `SOURCE_BUILD_PASS`; Linux application image produced.
- Windows: `WINDOWS_EXE_NOT_BUILT_ENVIRONMENT_LIMITATION`; `packageExe` was
  explicitly attempted and reported `SKIPPED` because the host OS is Linux.
- Galaxy S10: `UNTESTED_ON_REAL_DEVICE`.
- Android backup snapshot/restore: source builds and lint passes, but runtime
  SQLite/foreground-service behavior is `UNTESTED_ON_REAL_DEVICE`.

No mock or JVM integration result is presented as a real-device result.
