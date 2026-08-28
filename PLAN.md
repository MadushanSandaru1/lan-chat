# LAN Chat implementation plan

## Scope
Java 25 / Maven desktop application, JavaFX + FXML + CSS, UDP multicast discovery,
framed TCP messaging, SQLite history. No cloud, registration, scanning, or Swing.

## Milestones
1. Foundation: configuration, stable profile, immutable models, bounded JSON validation.
2. Discovery: interface selection, source-address identity, heartbeat, timeout, reconnect.
3. Messaging: framed TCP, HELLO, connection reuse, receipts, typing, graceful shutdown.
4. Desktop: searchable peers, bubbles, unread counts, settings, first-run profile.
5. Reliability: SQLite, failed sends, tests for malicious frames and loopback sessions.
6. Delivery: Maven build, OS-matrix CI, jpackage scripts and operating documentation.

## Design decisions
- UUID is identity; datagram source supplies address. Discovery does not authenticate identity.
- TCP sessions bind all subsequent envelopes to the HELLO identity and local recipient.
- Incoming chats are persisted before delivery acknowledgement; duplicate IDs are idempotent.
- Status transitions never regress after a fast acknowledgement.
- Network and database work run off the JavaFX thread; UI receives immutable snapshots.
- Version 1.0 traffic and local history are **not encrypted**. Cryptographic primitives
  are isolated and not wired into the protocol; authenticated pairing is future work.
- No automatic retransmission of ambiguous sends; failed messages can be retried manually.
- Native notifications are best-effort using AWT SystemTray (not Swing), with an in-app fallback.

## Verification
Unit tests: registry expiry, JSON, validation, frame boundaries, profile persistence,
SQLite deduplication/statuses/history, crypto roundtrip. Integration tests: loopback
HELLO, bidirectional delivery/read receipts, malformed clients, reconnect, shutdown.
GUI smoke test loads FXML and exercises the rendered scene when a display is available.
Physical two-device/firewall testing and signed installers remain manual release gates.

## Completion

All six implementation milestones are complete. See `VERIFICATION.md` for actual
test results and unverified release gates, and `README.md` for operation and packaging.
The implementation uses 37 focused Java classes rather than adding empty facade
classes solely to mirror every suggested filename in the source prompt.
