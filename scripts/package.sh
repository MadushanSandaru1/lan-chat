#!/usr/bin/env sh
# Run on the target OS. Pass app-image (default), dmg, or deb.
set -eu
package_type="${1:-app-image}"
case "$package_type" in app-image|dmg|deb) ;; *) echo 'Expected app-image, dmg or deb' >&2; exit 2 ;; esac
mvn --batch-mode --no-transfer-progress clean package
mkdir -p target/package-input/lib
cp target/lan-chat-1.0.0.jar target/package-input/
cp target/lib/*.jar target/package-input/lib/
jpackage --type "$package_type" --name 'LAN Chat' --app-version 1.0.0 \
  --input target/package-input --main-jar lan-chat-1.0.0.jar --main-class com.lanchat.Launcher \
  --dest dist --vendor 'LAN Chat' --description 'Peer-to-peer local network chat' \
  --java-options '--enable-native-access=ALL-UNNAMED' \
  --add-modules java.base,java.desktop,java.sql,java.logging,java.naming,java.management,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.charsets
