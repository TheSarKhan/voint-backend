package com.starsoft.voint.settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Encrypts provider secrets before they touch the database.
 *
 * <p>AES-GCM with a random 12-byte IV per value, stored as {@code base64(iv || ciphertext+tag)}.
 * GCM rather than CBC because it authenticates: a tampered row fails to decrypt instead of
 * silently yielding garbage that would then be sent to a provider as if it were a key.
 *
 * <p>The master key comes from configuration and never from the database - otherwise the
 * encryption would be protecting the secrets with something stored right next to them.
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${voint.secrets.master-key:}") String masterKey,
                        @Value("${voint.jwt.secret}") String jwtSecret) {
        String source = StringUtils.hasText(masterKey) ? masterKey : jwtSecret;
        if (!StringUtils.hasText(masterKey)) {
            log.warn("voint.secrets.master-key is not set - deriving the settings encryption key from "
                    + "the JWT secret. This works, but rotating the JWT secret would make every stored "
                    + "provider credential unreadable. Set VOINT_SECRETS_KEY to decouple them.");
        }
        // SHA-256 gives a valid 256-bit AES key from a passphrase of any length.
        this.key = new SecretKeySpec(sha256(source), "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt setting value", e);
        }
    }

    /** @return the plaintext, or {@code null} if the value cannot be decrypted (wrong master key). */
    public String decrypt(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Returning null rather than throwing keeps a single unreadable row from taking down
            // the whole settings page; the caller falls back to the configured value.
            log.error("Could not decrypt a stored setting - the master key may have changed", e);
            return null;
        }
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
