# Verification record

Local verification: 2026-08-29, macOS arm64, Java 25.0.4, Maven 3.9.16.

## Passed

- Java 25 compilation as part of the clean Maven build
- `mvn clean package` (19 standard tests passed; two optional tests skipped)
- `mvn -Dlanchat.guiTest=true -Dlanchat.networkTest=true test`:
  **21 tests passed, zero failures/errors/skips**.
- Actual multicast discovery: two temporary identities discovered each other on
  the available Wi-Fi interface, using the correct advertised TCP ports.
- Real loopback TCP sessions: HELLO, bidirectional messaging, delivery/read ACKs,
  duplicate suppression, typing, malformed clients, wrong peer identity,
  port fallback, failed-send retry after a peer becomes reachable.
- JavaFX smoke test loaded all three FXML resources, displayed the main window,
  selected a fixture peer and rendered conversation history. Screenshot inspected
  at `target/ui-smoke.png`; fixture messages exist only in temporary test data.
- `sh scripts/package.sh app-image` produced a macOS arm64 application bundle
  with embedded runtime under `dist/LAN Chat.app` (approximately 104 MB).

## Not yet verified / release gates

- Two separate physical computers, including firewall/client-isolation behavior.
- Live Wi-Fi disconnect/reconnect and DHCP changes (recovery code is implemented).
- Windows/Linux CI runs and their native installers; CI configuration is provided.
- Native notifications on every desktop environment.
- Installer signing/notarization, penetration testing and long-running load tests.

The app is implemented and locally tested, but this does not certify production
readiness. v1 network traffic and stored chat history are unencrypted.

## Toolchain warnings

The classpath-based JavaFX test emits an unnamed-module warning. Mockito may emit
a dynamic-agent warning on Java 25. Both are non-fatal in this verified environment.
