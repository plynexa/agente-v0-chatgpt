# Pairing and LAN Security — Phase 10

## Default boundary

The Android host binds to `127.0.0.1:8787` by default. Only `/v1/health`,
`/v1/pairing/status` and `/v1/pair` are public. Every administrative REST route
and the WebSocket event stream require a paired-device bearer token.

## Pairing flow

1. Start the Agent Core on Android.
2. Tap **Parear Windows**. The server temporarily binds to the LAN and displays
   its Wi-Fi IPv4 address plus a six-digit code.
3. In Windows Settings, enter `http://<S10-IP>:8787`, reconnect, and enter the
   code shown by Android.
4. The code expires after five minutes, is single-use and is invalidated after
   five failed attempts.
5. The host returns a 256-bit random device token once. The plaintext token is
   never stored by the host or written to logs.
6. Android stores a SHA-256 digest salted with a secret pepper held in encrypted
   preferences. The paired device metadata and hash remain in SQLite.
7. Windows protects the returned token with the current user's Windows DPAPI.
   The non-Windows preferences fallback exists only for local development.

After a successful pairing, LAN remains explicitly enabled across host restarts
and only valid paired tokens can access state. **Desativar LAN** immediately
rebinds the server to localhost. Paired tokens can also be revoked by the
authenticated device endpoint.

If the temporary pairing window expires without success, the server returns to
localhost automatically. Android force-stop and manufacturer networking/battery
behavior still require real Galaxy S10 validation.

## Recovery and reconnect

WebSocket events are live notifications, not the source of truth. The Windows
client reconnects the stream and refreshes authoritative REST state. Therefore,
events missed during a network outage do not erase conversations, memory,
tasks or reminders.

`PairingLanIntegrationTest` proves that:

- an unauthenticated administrative request receives HTTP 401;
- a valid one-time code pairs a Windows client;
- SQLite contains a hash rather than the plaintext token;
- authenticated chat returns a response and emits live WebSocket events;
- a newly created client using the same token recovers the persisted
  conversation and messages.

This is integration evidence, not a real Wi-Fi/Galaxy S10 test.
