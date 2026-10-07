package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.illustration.IllustrationPersistence;
import com.doova.ktab.features.storybook.illustration.PhotoPurgeHandler;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.features.storybook.web.dto.CharacterInput;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * End-to-end demonstration test showing how real image files are sanitized,
 * encrypted via AES-256-GCM, stored in the asset store, decrypted for character sheets,
 * and purged upon completion.
 */
class RealPhotoIntakeStorageDemoTest {

    private static final String REAL_IMAGE_PATH = "/storybook/styles/soft_watercolor.png";
    // Using a sample 32-byte AES key (Base64)
    private static final String AES_256_KEY_BASE64 = "xMLqzqBD6dLjVlNG0j7hBZcIX5aJzudWxD/jkI9uMjE=";

    @Test
    @DisplayName("Demonstrates real image intake: sanitization, AES-256-GCM encryption, asset storage, and decryption")
    void testRealImageIntakeAndStorageLifecycle() throws Exception {
        // 1. Load real image bytes (the 31 KB production artwork)
        java.nio.file.Path prodPath = java.nio.file.Path.of("src/main/resources/storybook/styles/soft_watercolor.png");
        byte[] realImageBytes = java.nio.file.Files.exists(prodPath)
                ? java.nio.file.Files.readAllBytes(prodPath)
                : getClass().getResourceAsStream("/storybook/styles/soft_watercolor.png").readAllBytes();
        assertThat(realImageBytes.length).isGreaterThan(1000);

        // Verify the original is a valid image with PNG magic bytes (0x89 0x50 0x4E 0x47)
        assertThat(realImageBytes[0]).isEqualTo((byte) 0x89);
        assertThat(realImageBytes[1]).isEqualTo((byte) 0x50);
        assertThat(realImageBytes[2]).isEqualTo((byte) 0x4E);
        assertThat(realImageBytes[3]).isEqualTo((byte) 0x47);

        BufferedImage originalImg = ImageIO.read(new ByteArrayInputStream(realImageBytes));
        assertThat(originalImg).isNotNull();
        System.out.printf("--- 1. Original Image Loaded ---%n");
        System.out.printf("Format: PNG | Dimensions: %dx%d | Size: %d bytes%n",
                originalImg.getWidth(), originalImg.getHeight(), realImageBytes.length);

        // 2. Setup real PhotoVault with configured 32-byte AES key
        StorybookProperties props = new StorybookProperties();
        props.getPhoto().setEncryptionKey(AES_256_KEY_BASE64);
        PhotoVault vault = new PhotoVault(props);

        // Setup real in-memory store simulation to capture stored keys and payloads
        Map<String, byte[]> capturedStore = new ConcurrentHashMap<>();
        StorybookAssetStore store = mock(StorybookAssetStore.class);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            byte[] bytes = invocation.getArgument(1);
            capturedStore.put(key, bytes);
            return null;
        }).when(store).put(anyString(), any(byte[].class), anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return capturedStore.get(key);
        }).when(store).get(anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return capturedStore.containsKey(key);
        }).when(store).exists(anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            capturedStore.remove(key);
            return null;
        }).when(store).delete(anyString());

        // Setup mocks for guard and repository
        StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
        StorybookCharacterRepository charRepo = mock(StorybookCharacterRepository.class);

        Long bookId = 42L;
        User user = new User();
        Storybook book = new Storybook();
        book.setId(bookId);
        book.setStatus(StorybookStatus.DRAFT);
        when(guard.requireOwned(bookId, user)).thenReturn(book);

        StorybookCharacter childChar = new StorybookCharacter();
        childChar.setId(1L);
        childChar.setCharacterId("child");
        childChar.setKind(CharacterKind.CHILD);

        StorybookCharacter companionChar = new StorybookCharacter();
        companionChar.setId(2L);
        companionChar.setCharacterId("companion");
        companionChar.setKind(CharacterKind.COMPANION);

        StorybookCharacter supportingChar = new StorybookCharacter();
        supportingChar.setId(3L);
        supportingChar.setCharacterId("grandpa");
        supportingChar.setKind(CharacterKind.SUPPORTING);

        when(charRepo.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD)).thenReturn(Optional.of(childChar));
        when(charRepo.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION)).thenReturn(Optional.of(companionChar));
        when(charRepo.findByStorybook_Id(bookId)).thenReturn(List.of(childChar, companionChar, supportingChar));
        when(charRepo.findByStorybook_IdAndCharacterId(bookId, "grandpa")).thenReturn(Optional.of(supportingChar));

        PhotoIntakeService intakeService = new PhotoIntakeService(guard, charRepo, store, vault, props);

        // 3. Process Child Photo through intake
        MockMultipartFile childMultipart = new MockMultipartFile(
                "childPhoto", "child_photo.png", "image/png", realImageBytes
        );
        intakeService.upload(user, bookId, childMultipart, true);

        // 4. Verify storage key and encrypted blob characteristics
        String childStorageKey = "storybook/42/photo/source.enc";
        assertThat(capturedStore).containsKey(childStorageKey);
        byte[] encryptedBlob = capturedStore.get(childStorageKey);

        System.out.printf("%n--- 2. Stored Encrypted Asset ---%n");
        System.out.printf("Storage Key: %s%n", childStorageKey);
        System.out.printf("Stored Blob Size: %d bytes%n", encryptedBlob.length);

        // Verify the stored blob is NOT readable as an image
        BufferedImage unreadableAttempt = ImageIO.read(new ByteArrayInputStream(encryptedBlob));
        assertThat(unreadableAttempt).as("Encrypted blob cannot be read as an image").isNull();

        // Verify the stored blob does NOT contain JPEG (0xFF 0xD8) or PNG (0x89 0x50) magic bytes
        boolean hasJpegHeader = (encryptedBlob[0] == (byte) 0xFF && encryptedBlob[1] == (byte) 0xD8);
        boolean hasPngHeader = (encryptedBlob[0] == (byte) 0x89 && encryptedBlob[1] == (byte) 0x50);
        assertThat(hasJpegHeader).isFalse();
        assertThat(hasPngHeader).isFalse();

        // Verify IV: The first 12 bytes are the AES-GCM IV
        byte[] iv = new byte[12];
        System.arraycopy(encryptedBlob, 0, iv, 0, 12);
        System.out.printf("AES-GCM Random IV (first 12 bytes hex): %02x%02x%02x%02x...%n",
                iv[0], iv[1], iv[2], iv[3]);

        // 5. Decrypt using PhotoVault (as CharacterSheetHandler does)
        byte[] decryptedBytes = vault.decrypt(encryptedBlob);
        System.out.printf("%n--- 3. In-Memory Decryption ---%n");
        System.out.printf("Decrypted Size: %d bytes%n", decryptedBytes.length);

        // Decrypted data is a sanitized JPEG (SOI marker 0xFF 0xD8 0xFF)
        assertThat(decryptedBytes[0]).isEqualTo((byte) 0xFF);
        assertThat(decryptedBytes[1]).isEqualTo((byte) 0xD8);
        assertThat(decryptedBytes[2]).isEqualTo((byte) 0xFF);

        // Read decrypted image to verify it's completely intact
        BufferedImage decryptedImg = ImageIO.read(new ByteArrayInputStream(decryptedBytes));
        assertThat(decryptedImg).isNotNull();
        System.out.printf("Decrypted Image Validated: Dimensions=%dx%d%n",
                decryptedImg.getWidth(), decryptedImg.getHeight());

        // 6. Test Tampering Detection (AES-GCM Authenticated Encryption)
        byte[] tamperedBlob = encryptedBlob.clone();
        tamperedBlob[tamperedBlob.length - 1] ^= 0x01; // flip 1 bit in auth tag / ciphertext
        assertThatThrownBy(() -> vault.decrypt(tamperedBlob))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Photo decryption failed (wrong key or tampered data)");
        System.out.printf("Tamper Resistance Confirmed: Modified byte rejected by AES-GCM auth tag.%n");

        // 7. Test Companion & Supporting Character Photo intake via attachPhotos
        MockMultipartFile companionMultipart = new MockMultipartFile(
                "companionPhoto", "pet.png", "image/png", realImageBytes
        );
        MockMultipartFile grandpaMultipart = new MockMultipartFile(
                "characterPhotos", "grandpa.png", "image/png", realImageBytes
        );

        CompanionSpec companion = new CompanionSpec(
                CompanionSpec.CompanionType.CAT,
                "Mimi",
                null,
                CompanionSpec.PetColor.ORANGE
        );
        CharacterInput grandpa = new CharacterInput(
                "grandpa", "Grandpa", "human", "mentor", "grandfather", "vest", null, null
        );

        CreateStorybookRequest createReq = new CreateStorybookRequest(
                null, null, null, companion, null, null, null,
                com.doova.ktab.features.storybook.enums.ArtStyle.SOFT_WATERCOLOR,
                15, null, null, null, null, null, null, null, null, null, List.of(grandpa), null, null, true
        );

        intakeService.attachPhotos(bookId, createReq, null, companionMultipart, List.of(grandpaMultipart), true);

        assertThat(capturedStore).containsKey("storybook/42/photo/companion.enc");
        assertThat(capturedStore).containsKey("storybook/42/photo/grandpa.enc");
        System.out.printf("%n--- 4. Multi-Character Storage Keys ---%n");
        System.out.printf("Child Key: %s%n", childStorageKey);
        System.out.printf("Companion Key: storybook/42/photo/companion.enc%n");
        System.out.printf("Supporting Key: storybook/42/photo/grandpa.enc%n");

        // 8. Test Purge Lifecycle: PhotoPurgeHandler deletes all photo keys after sheets are generated
        IllustrationPersistence persistence = mock(IllustrationPersistence.class);
        when(persistence.allPhotoKeys(bookId)).thenReturn(List.of(
                "storybook/42/photo/source.enc",
                "storybook/42/photo/companion.enc",
                "storybook/42/photo/grandpa.enc"
        ));

        PhotoPurgeHandler purgeHandler = new PhotoPurgeHandler(store, persistence);
        StorybookJob purgeJob = new StorybookJob();
        purgeJob.setStorybookId(bookId);

        purgeHandler.handle(purgeJob);

        // Verify all encrypted photos have been wiped from the store
        assertThat(capturedStore).doesNotContainKey("storybook/42/photo/source.enc");
        assertThat(capturedStore).doesNotContainKey("storybook/42/photo/companion.enc");
        assertThat(capturedStore).doesNotContainKey("storybook/42/photo/grandpa.enc");
        verify(persistence).markPhotoPurged(bookId);
        System.out.printf("%n--- 5. Purge Lifecycle Confirmed ---%n");
        System.out.printf("All photos permanently deleted from asset store after character sheets produced.%n");
    }
}
