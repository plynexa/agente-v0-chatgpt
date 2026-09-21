# Windows Client — Phase 9

The Compose Desktop application is a remote client. It does not create or own
an Agent Core or a second database. The configured Android host remains the
source of truth.

## Connected screens

- Dashboard: host status, queue, active tasks, memories, reminders, backup and
  provider summary.
- Chat: persistent bidirectional chat in the active host conversation.
- Memory: create, search, edit and delete typed host memories.
- Projects and Skills: live host lists.
- Tasks: live states and cancellation for unfinished tasks.
- Reminders: live list plus explicit date/time creation, edit and delete.
- History: create/select/rename/delete conversations and inspect original messages.
- Devices: host-reported device/capability list and revocation.
- Models: manual provider name/URL/model/secret configuration; execution remains partial.
- Logs: real-time WebSocket activity with readable Portuguese event labels and
  useful metadata, capped to 200 UI rows.
- Settings: persisted host URL, explicit reconnect and automatic LAN discovery.

Chat input enters the S10 `AgentConversationRuntime`; the Desktop application
does not generate local answers. Enter sends the message and Shift+Enter creates
a new line.

V0.3 starts independent REST loads concurrently and debounces bursts of live
events. Each installation keeps a stable client id, so pairing again after an
IP change updates the same device instead of creating duplicates. The discovery
button scans the computer's active IPv4 `/24` networks for port 8787; this
convenience is limited to the same local subnet and still requires authorization.

`AgentRemoteClient` is shared Kotlin code. It uses typed REST models and a
WebSocket event loop with reconnect. When an event arrives, the relevant panel
is refreshed from the authoritative host state, so reconnection does not rely
on replaying every missed event.

## Run and build

For local development with a host on the same machine:

```shell
./gradlew :desktopApp:run
```

On Windows, double-click `ABRIR_AGENT_WINDOWS.bat`. Use `TESTAR_AGENT.bat` for
the shared tests/Desktop compilation, `COMPILAR_ANDROID.bat` for the debug APK,
and `INSTALAR_S10.bat` when ADB is available.

Compile the Desktop source:

```shell
./gradlew :desktopApp:compileKotlinDesktop
```

The default endpoint is `http://127.0.0.1:8787`. On Windows, enter the paired
Galaxy S10 LAN address in Settings. `GERAR_EXE_WINDOWS.bat` runs the Compose
`packageExe` and `packageMsi` tasks and prints their output folders. Native
Windows packages must be generated and validated on Windows; a Linux source
build is not evidence of an `.exe`.

## Security boundary

The Settings screen performs the Phase 10 one-time-code flow. The token is
protected with Windows DPAPI for the current OS user and injected into both REST
and WebSocket requests. It is never rendered in the UI or logs. On Linux/macOS,
the preferences fallback is development-only and is not classified as secure
production storage. See `docs/PAIRING_AND_LAN.md`.
