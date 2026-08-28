package com.lanchat.validation;

import com.lanchat.model.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolValidatorTest {
    private final String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    @Test void acceptsValidChatButRejectsSpoofedIdentity() {
        var m = ProtocolMessage.event(MessageType.CHAT_MESSAGE, a, b, null, "Hello 🌍");
        assertDoesNotThrow(() -> ProtocolValidator.message(m, a, b));
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.message(m, b, a));
    }
    @Test void validatesUtf8BytesNotCharacters() {
        assertDoesNotThrow(() -> ProtocolValidator.text("a".repeat(10240)));
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.text("🌍".repeat(2561)));
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.text(" \n"));
    }
    @Test void rejectsMissingMalformedAndNonCanonicalIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.uuid(null));
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.uuid("1-1-1-1-1"));
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.hello(ProtocolMessage.hello(a, "Alice"), b));
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.name("Alice\nInjected"));
    }
    @Test void rejectsUnsupportedVersionAndBadTimestamp() {
        var m = new ProtocolMessage(MessageType.CHAT_MESSAGE, "2.0", a, a, b, "hello", 1, null, null);
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.message(m, a, b));
        var invalid = new DiscoveryMessage("DISCOVERY", "LAN_CHAT", "1.0", a, "A", "Device", 0, 1);
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.discovery(invalid));
        assertThrows(IllegalArgumentException.class, () -> ProtocolValidator.hello(new ProtocolMessage(MessageType.HELLO, "1.0", null, null, null, null, -1, a, "A"), null));
    }
}
