package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PhotoVaultTest {

    public static StorybookProperties withKey() {
        StorybookProperties p = new StorybookProperties();
        p.getPhoto().setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return p;
    }

    @Test
    void roundTrips() {
        PhotoVault vault = new PhotoVault(withKey());
        byte[] sealed = vault.encrypt("photo bytes".getBytes());
        assertThat(sealed).isNotEqualTo("photo bytes".getBytes());
        assertThat(new String(vault.decrypt(sealed))).isEqualTo("photo bytes");
    }

    @Test
    void twoEncryptionsDiffer() {
        PhotoVault vault = new PhotoVault(withKey());
        assertThat(vault.encrypt(new byte[]{1})).isNotEqualTo(vault.encrypt(new byte[]{1}));
    }

    @Test
    void tamperingIsDetected() {
        PhotoVault vault = new PhotoVault(withKey());
        byte[] sealed = vault.encrypt(new byte[]{1, 2, 3});
        sealed[sealed.length - 1] ^= 1;
        assertThatThrownBy(() -> vault.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingKeyIsAConfigurationError() {
        assertThatThrownBy(() -> new PhotoVault(new StorybookProperties()).encrypt(new byte[]{1}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("KTAB_STORYBOOK_PHOTO_KEY");
    }
}
