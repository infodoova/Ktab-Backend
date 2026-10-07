package com.doova.ktab.features.storybook.character;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PhotoIntakeServiceTest {

    private final StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
    private final StorybookCharacterRepository characters = mock(StorybookCharacterRepository.class);
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final PhotoVault vault = new PhotoVault(PhotoVaultTest.withKey());
    private final PhotoIntakeService service = new PhotoIntakeService(guard, characters, store, vault, PhotoVaultTest.withKey());

    private final User owner = new User();
    private final Storybook book = new Storybook();
    private final StorybookCharacter child = new StorybookCharacter();

    @BeforeEach
    void setUp() {
        book.setId(5L);
        book.setStatus(StorybookStatus.DRAFT);
        when(guard.requireOwned(5L, owner)).thenReturn(book);
        when(characters.findByStorybook_IdAndKind(5L, CharacterKind.CHILD)).thenReturn(Optional.of(child));
    }

    private static MockMultipartFile jpeg(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        return new MockMultipartFile("photo", "kid.jpg", "image/jpeg", out.toByteArray());
    }

    @Test
    void consentIsRequired() throws Exception {
        assertThatThrownBy(() -> service.upload(owner, 5L, jpeg(10, 10), false))
                .isInstanceOf(BadRequestException.class).hasMessage("STORYBOOK_PHOTO_CONSENT_REQUIRED");
        verifyNoInteractions(store);
    }

    @Test
    void notAllowedAfterStoryApproval() throws Exception {
        book.setStatus(StorybookStatus.STORY_READY);
        book.setStoryApprovedAt(java.time.Instant.now());
        assertThatThrownBy(() -> service.upload(owner, 5L, jpeg(10, 10), true))
                .isInstanceOf(StorybookStateConflictException.class);
    }

    @Test
    void rejectsNonImages() {
        MockMultipartFile text = new MockMultipartFile("photo", "x.jpg", "image/jpeg", "not an image".getBytes());
        assertThatThrownBy(() -> service.upload(owner, 5L, text, true)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void reencodesTheUpload() throws Exception {
        service.upload(owner, 5L, jpeg(4000, 3000), true);

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(store).put(eq("storybook/5/photo/source.enc"), stored.capture(), eq("application/octet-stream"));
        BufferedImage plain = ImageIO.read(new ByteArrayInputStream(vault.decrypt(stored.getValue())));
        assertThat(Math.max(plain.getWidth(), plain.getHeight())).isEqualTo(2048);
        assertThat(child.getPhotoKey()).isEqualTo("storybook/5/photo/source.enc");
        assertThat(child.getPhotoConsentAt()).isNotNull();
    }

    @Test
    void attachPhotoBase64_storesEncryptedJpegAndSetsCharacterPhotoKey() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        String base64 = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());

        service.attachPhotoBase64(5L, "child", base64, true);

        assertThat(child.getPhotoKey()).isEqualTo("storybook/5/photo/source.enc");
        assertThat(child.getPhotoConsentAt()).isNotNull();
        verify(store).put(eq("storybook/5/photo/source.enc"), any(), eq("application/octet-stream"));
    }

    @Test
    void supportingCharacterPhotoUploadIsStoredWithCharacterSlug() throws Exception {
        StorybookCharacter friend = new StorybookCharacter();
        friend.setCharacterId("friend-sam");
        friend.setKind(CharacterKind.SUPPORTING);
        when(characters.findByStorybook_IdAndCharacterId(5L, "friend-sam")).thenReturn(Optional.of(friend));

        service.attachPhoto(5L, "friend-sam", jpeg(50, 50), true);

        assertThat(friend.getPhotoKey()).isEqualTo("storybook/5/photo/friend-sam.enc");
        assertThat(friend.getPhotoConsentAt()).isNotNull();
        verify(store).put(eq("storybook/5/photo/friend-sam.enc"), any(), eq("application/octet-stream"));
    }
}
