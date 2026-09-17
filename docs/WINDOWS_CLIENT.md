# Windows Client — Phase 9

The Compose Desktop application is a remote client. It does not create or own
an Agent Core or a second database. The configured Android host remains the
source of truth.

## Connected screens

- Dashboard: host status, queue, active tasks, memories, reminders, backup and
  provider summary.
- Chat: persistent bidirectional chat in the active host conversation.
- Memory: create, list and search typed host memories.
- Projects and Skills: live host lists.
- Tasks: live states and cancellation for unfinished tasks.
- Reminders: live list and durable creation (the V0 button schedules +10 min).
- History: conversation selection and complete original messages.
- Devices: host-reported device/capability list.
- Models: honest local/optional provider classification.
- Logs: real-time WebSocket event activity, capped to 200 UI rows.
- Settings: persisted host URL and explicit reconnect.

`AgentRemoteClient` is shared Kotlin code. It uses typed REST models and a
WebSocket event loop with reconnect. When an event arrives, the relevant panel
is refreshed from the authoritative host state, so reconnection does not rely
on replaying every missed event.

## Run and build

For local development with a host on the same machine:

```shell
./gradlew :desktopApp:run
```

Compile the Desktop source:

```shell
./gradlew :desktopApp:compileKotlinDesktop
```

The default endpoint is `http://127.0.0.1:8787`. On Windows, enter the paired
Galaxy S10 LAN address in Settings. Native Windows `.exe` packaging must be run
and validated on Windows; a Linux source build is not evidence of an `.exe`.

## Security boundary

The Settings screen performs the Phase 10 one-time-code flow. The token is
protected with Windows DPAPI for the current OS user and injected into both REST
and WebSocket requests. It is never rendered in the UI or logs. On Linux/macOS,
the preferences fallback is development-only and is not classified as secure
production storage. See `docs/PAIRING_AND_LAN.md`.
