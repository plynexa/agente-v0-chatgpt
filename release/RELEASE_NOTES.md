# Agent V0 ChatGPT — Release Notes

Release date: 2026-09-16

This is the first source-complete local-first V0. The Android debug APK and a
source archive are produced after the final verification run. SHA-256 values are
recorded in `SHA256SUMS.txt`.

Validated in this environment:

- 20/20 automated tests;
- Android debug APK build and lint;
- Desktop JVM source build;
- Linux Desktop application image;
- local persistence, memory, context, tasks, reminders, backup, voice state
  machine, authenticated pairing/chat/events and reconnect contract.

Not validated here:

- installation or runtime behavior on the Galaxy S10;
- real wake word/STT/TTS and acoustic barge-in;
- Wi-Fi connection to a physical Windows machine;
- Windows DPAPI runtime and native `.exe` packaging.

Read the root `README.md` and `docs/TEST_MATRIX.md` before installation.
