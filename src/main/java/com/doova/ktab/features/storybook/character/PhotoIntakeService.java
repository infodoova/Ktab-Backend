package com.doova.ktab.features.storybook.character;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.image.ImageDownscaler;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PhotoIntakeService {

    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png");

    private final StorybookAccessGuard guard;
    private final StorybookCharacterRepository characters;
    private final StorybookAssetStore store;
    private final PhotoVault vault;
    private final StorybookProperties properties;

    @Transactional
    public void upload(User owner, Long bookId, MultipartFile photo, boolean consent) {
        if (!consent) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_PHOTO_CONSENT_REQUIRED);
        }
        Storybook book = guard.requireOwned(bookId, owner);
        boolean beforeSheet = book.getStatus() == StorybookStatus.DRAFT
                || (book.getStatus() == StorybookStatus.STORY_READY && book.getStoryApprovedAt() == null);
        if (!beforeSheet) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        if (photo == null || photo.isEmpty() || !TYPES.contains(photo.getContentType())
                || photo.getSize() > properties.getPhoto().getMaxBytes()) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        byte[] clean;
        try {
            // Re-encoding drops EXIF (GPS, device) and caps the size sent to the image model.
            clean = ImageDownscaler.toJpeg(photo.getBytes(), 2048);
        } catch (IllegalArgumentException | IOException e) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        String key = StorybookKeys.photo(bookId);
        store.put(key, vault.encrypt(clean), "application/octet-stream");

        StorybookCharacter child = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElseThrow();
        child.setPhotoKey(key);
        child.setPhotoConsentAt(Instant.now());
    }
}
