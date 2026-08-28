package com.lanchat.validation;

import com.lanchat.config.NetworkConfig;
import com.lanchat.model.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ProtocolValidator {
    private ProtocolValidator() {}
    public static void uuid(String value) {
        if (value == null || !UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Invalid UUID");
    }
    public static void name(String value) {
        if (value == null || value.isBlank() || value.length() > 80 || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Names must be 1–80 characters without control characters");
    }
    public static void text(String value) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > NetworkConfig.MAX_TEXT)
            throw new IllegalArgumentException("Message must contain 1–10240 UTF-8 bytes");
    }
    private static void common(String version, long timestamp) {
        if (!NetworkConfig.VERSION.equals(version)) throw new IllegalArgumentException("Unsupported protocol version");
        if (timestamp <= 0) throw new IllegalArgumentException("Invalid timestamp");
    }
    public static void discovery(DiscoveryMessage m) {
        if (m == null || !"DISCOVERY".equals(m.type()) || !"LAN_CHAT".equals(m.app())) throw new IllegalArgumentException("Not LAN Chat discovery");
        common(m.protocolVersion(), m.timestamp()); uuid(m.deviceId()); name(m.displayName()); name(m.deviceName());
        if (m.chatPort() < 1 || m.chatPort() > 65535) throw new IllegalArgumentException("Invalid port");
    }
    public static void hello(ProtocolMessage m, String expected) {
        if (m == null || m.type() != MessageType.HELLO) throw new IllegalArgumentException("HELLO required");
        common(m.protocolVersion(), m.timestamp()); uuid(m.deviceId()); name(m.displayName());
        if (expected != null && !expected.equals(m.deviceId())) throw new IllegalArgumentException("Unexpected peer identity");
    }
    public static void message(ProtocolMessage m, String remote, String local) {
        if (m == null || m.type() == null || m.type() == MessageType.HELLO) throw new IllegalArgumentException("Invalid message type");
        common(m.protocolVersion(), m.timestamp()); uuid(m.messageId()); uuid(m.senderId()); uuid(m.receiverId());
        if (!remote.equals(m.senderId()) || !local.equals(m.receiverId())) throw new IllegalArgumentException("Session identity mismatch");
        if (m.type() == MessageType.CHAT_MESSAGE) text(m.content());
        else if (m.content() != null) throw new IllegalArgumentException("Control event cannot contain chat text");
    }
}
