param([ValidateSet('app-image', 'exe', 'msi')][string]$Type = 'app-image')
$ErrorActionPreference = 'Stop'
mvn --batch-mode --no-transfer-progress clean package
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
New-Item -ItemType Directory -Force target/package-input/lib | Out-Null
Copy-Item target/lan-chat-1.0.0.jar target/package-input/
Copy-Item target/lib/*.jar target/package-input/lib/
jpackage --type $Type --name 'LAN Chat' --app-version 1.0.0 `
  --input target/package-input --main-jar lan-chat-1.0.0.jar --main-class com.lanchat.Launcher `
  --dest dist --vendor 'LAN Chat' --description 'Peer-to-peer local network chat' `
  --java-options '--enable-native-access=ALL-UNNAMED' `
  --add-modules java.base,java.desktop,java.sql,java.logging,java.naming,java.management,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.charsets
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
