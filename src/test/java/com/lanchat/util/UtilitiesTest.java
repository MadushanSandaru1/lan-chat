package com.lanchat.util;

import com.lanchat.model.*;
import com.lanchat.security.*;
import org.junit.jupiter.api.Test;
import javax.crypto.KeyGenerator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class UtilitiesTest {
    @Test void serializesDiscoveryAndUnicodeChat() throws Exception {
        var discovery = new DiscoveryMessage("DISCOVERY", "LAN_CHAT", "1.0", UUID.randomUUID().toString(), "Alice", "Laptop", 1234, 1);
        assertEquals(discovery, JsonUtil.decode(JsonUtil.encode(discovery), DiscoveryMessage.class));
        var chat = new ChatMessage("id", "a", "b", "ආයුබෝවන් 👋", 1, MessageStatus.SENT);
        assertEquals(chat, JsonUtil.decode(JsonUtil.encode(chat), ChatMessage.class));
    }
    @Test void filtersVirtualAdapters() {
        assertTrue(NetworkUtil.usableName("en0")); assertTrue(NetworkUtil.usableName("wlan0"));
        assertFalse(NetworkUtil.usableName("docker0")); assertFalse(NetworkUtil.usableName("utun4")); assertFalse(NetworkUtil.usableName("veth123"));
    }
    @Test void isolatedCryptoPrimitivesAuthenticateCiphertext() throws Exception {
        var crypto = new CryptoService(); var keys = new KeyManager(); var a = keys.ephemeralKeyPair(); var b = keys.ephemeralKeyPair();
        assertArrayEquals(crypto.sharedSecret(a.getPrivate(), b.getPublic()), crypto.sharedSecret(b.getPrivate(), a.getPublic()));
        var generator = KeyGenerator.getInstance("AES"); generator.init(256); var key = generator.generateKey();
        var encrypted = crypto.encrypt(key, "hello".getBytes()); assertArrayEquals("hello".getBytes(), crypto.decrypt(key, encrypted));
        encrypted[15] ^= 1; assertThrows(java.security.GeneralSecurityException.class, () -> crypto.decrypt(key, encrypted));
    }
}
