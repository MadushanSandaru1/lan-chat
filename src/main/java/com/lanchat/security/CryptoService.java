package com.lanchat.security;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.*;
import java.util.Arrays;

/** Standalone primitives, NOT used by v1 network sessions. Pairing/KDF/authentication remain future work. */
public final class CryptoService {
    public byte[] sharedSecret(PrivateKey local, PublicKey remote) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519"); agreement.init(local); agreement.doPhase(remote, true); return agreement.generateSecret();
    }
    public byte[] encrypt(SecretKey key, byte[] plaintext) throws GeneralSecurityException {
        byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(plaintext);
        byte[] result = Arrays.copyOf(nonce, nonce.length + encrypted.length); System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length); return result;
    }
    public byte[] decrypt(SecretKey key, byte[] envelope) throws GeneralSecurityException {
        if (envelope.length < 28) throw new GeneralSecurityException("Invalid encrypted envelope");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(envelope, 12)));
        return cipher.doFinal(envelope, 12, envelope.length - 12);
    }
}
