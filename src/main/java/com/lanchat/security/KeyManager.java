package com.lanchat.security;

import java.security.*;

/** Future session-key provider. Keys are ephemeral and are never logged or hardcoded. */
public final class KeyManager {
    public KeyPair ephemeralKeyPair() throws GeneralSecurityException { return KeyPairGenerator.getInstance("X25519").generateKeyPair(); }
}
