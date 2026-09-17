# Android Host — Phase 8

## Runtime

`AgentHostService` is the authoritative V0 host process on Android. It is a
foreground service with a persistent notification, `START_STICKY` restart
policy, a supervisor-backed coroutine scope and an `AndroidAgentRuntime` that
owns:

- the SQLDelight `agent-v0.db` database;
- Agent Core and the current conversation;
- two concurrent task workers;
- the persistent reminder scheduler and Android notifications;
- the Ktor REST/WebSocket server on port `8787`;
- Voice Manager, Android offline speech recognition and Android TTS.

Closing the activity does not stop the service. The notification exposes
standby, microphone-off and stop actions. Stopping the service closes the API,
task workers, voice engines and database in order.

## Network safety

The API binds to `127.0.0.1` by default. **Parear Windows** opens a five-minute
pairing window, displays the phone's LAN address and a one-time code, and only a
successful pair persists `lan_enabled`. All administrative REST/WebSocket routes
require a device token. **Desativar LAN** rebinds to localhost. See
`docs/PAIRING_AND_LAN.md`.

## Voice behavior and honest classification

The implementation uses Android's on-device `SpeechRecognizer`, requests
offline recognition only, and never falls back to a network recognizer. It is
available only when the OS reports a local recognizer (Android 12/API 31 or
newer). Android TTS selects an installed Portuguese voice without the network
synthesis feature when one exists.

The wake-word loop, continuous conversation, transcript persistence, TTS and
state transitions are implemented, but their acoustic behavior is
`UNTESTED_ON_REAL_DEVICE`. Wake word/STT are therefore `PARTIAL` until tested on
the target Galaxy S10 with an installed Portuguese offline speech pack. The
barge-in transition is implemented and unit-tested, but Android acoustic
speech detection while TTS is playing remains `PARTIAL`.

`MIC_MUTED` stops and cancels both recognition modes. In this state there is no
wake word; explicit UI or notification action is required to reactivate voice.

## Permissions and battery behavior

- `RECORD_AUDIO` is requested only when the user enables voice.
- `POST_NOTIFICATIONS` is requested on Android 13+.
- Foreground-service microphone/data-sync types are declared visibly.
- Android cloud/device-transfer backup is disabled so database data and
  encrypted preferences are not exported outside the agent's own backup flow.
- The app opens Android's battery-optimization settings. It does not silently
  request or assume an exemption. Samsung battery settings may still stop a
  long-running service; this must be validated on the target device.

Android may restrict foreground service restarts or long-running work based on
OS version, user action and manufacturer policy. `START_STICKY` requests a
restart when the platform permits it; it is not a guarantee after force-stop.

## Build

Install JDK 17 and the official Android SDK with platform/build tools 35, then
create an uncommitted `local.properties`:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

Build and lint:

```shell
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
```

The debug APK is written to
`androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Real-device validation still required

1. Install the APK on the Galaxy S10 and grant notification/microphone access.
2. Confirm the service survives activity close and permissible process restart.
3. Confirm the installed OS exposes an on-device Portuguese recognizer.
4. Exercise wake word, continuous STT/TTS, standby and real microphone mute.
5. Validate reminder delivery, battery policy and LAN behavior on Wi-Fi.
