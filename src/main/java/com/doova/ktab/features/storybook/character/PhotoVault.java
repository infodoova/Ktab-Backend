package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** AES-256-GCM for the child's photo while it briefly exists (spec: "encryption while stored"). */
@Component
public class PhotoVault {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final StorybookProperties properties;
    private final SecureRandom random = new SecureRandom();

    public PhotoVault(StorybookProperties properties) {
        this.properties = properties;
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] body = cipher.doFinal(plain);
            byte[] out = Arrays.copyOf(iv, IV_BYTES + body.length);
            System.arraycopy(body, 0, out, IV_BYTES, body.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Photo encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] sealed) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
            return cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Photo decryption failed (wrong key or tampered data)", e);
        }
    }

    private SecretKeySpec key() {
        String encoded = properties.getPhoto().getEncryptionKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Set KTAB_STORYBOOK_PHOTO_KEY (base64 of 32 bytes) to accept photos");
        }
        byte[] raw = Base64.getDecoder().decode(encoded.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("KTAB_STORYBOOK_PHOTO_KEY must decode to 32 bytes");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
