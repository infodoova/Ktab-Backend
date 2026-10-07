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
import com.doova.ktab.features.storybook.web.dto.CharacterInput;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
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
        Storybook book = guard.requireOwned(bookId, owner);
        boolean beforeSheet = book.getStatus() == StorybookStatus.DRAFT
                || (book.getStatus() == StorybookStatus.STORY_READY && book.getStoryApprovedAt() == null);
        if (!beforeSheet) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        attachPhoto(bookId, null, photo, consent);
    }

    @Transactional
    public void attachPhoto(Long bookId, String characterId, MultipartFile photo, boolean consent) {
        if (photo == null || photo.isEmpty()) {
            return;
        }
        try {
            attachPhotoBytes(bookId, characterId, photo.getBytes(), photo.getContentType(), consent);
        } catch (IOException e) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }
    }

    @Transactional
    public void attachPhotoBase64(Long bookId, String characterId, String base64Data, boolean consent) {
        if (base64Data == null || base64Data.isBlank()) {
            return;
        }
        String contentType = null;
        String raw = base64Data.trim();
        if (raw.startsWith("data:")) {
            int commaIdx = raw.indexOf(',');
            if (commaIdx != -1) {
                String meta = raw.substring(5, commaIdx);
                int semiIdx = meta.indexOf(';');
                if (semiIdx != -1) {
                    contentType = meta.substring(0, semiIdx);
                }
                raw = raw.substring(commaIdx + 1);
            }
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }
        attachPhotoBytes(bookId, characterId, bytes, contentType, consent);
    }

    @Transactional
    public void attachPhotoBytes(Long bookId, String characterId, byte[] rawBytes, String contentType, boolean consent) {
        if (!consent) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_PHOTO_CONSENT_REQUIRED);
        }
        if (rawBytes == null || rawBytes.length == 0
                || (contentType != null && !contentType.isBlank() && !TYPES.contains(contentType.toLowerCase()))
                || rawBytes.length > properties.getPhoto().getMaxBytes()) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        byte[] clean;
        try {
            // Re-encoding drops EXIF (GPS, device) and caps the size sent to the image model.
            clean = ImageDownscaler.toJpeg(rawBytes, 2048);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        StorybookCharacter character = null;
        if (characterId != null && !characterId.isBlank() && !"child".equalsIgnoreCase(characterId)) {
            character = characters.findByStorybook_IdAndCharacterId(bookId, characterId).orElse(null);
            if (character == null) {
                character = characters.findByStorybook_Id(bookId).stream()
                        .filter(c -> c.getCharacterId() != null && c.getCharacterId().equalsIgnoreCase(characterId))
                        .findFirst().orElse(null);
            }
        }
        if (character == null && "companion".equalsIgnoreCase(characterId)) {
            character = characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION).orElse(null);
        }
        if (character == null) {
            character = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElse(null);
        }

        String key = (character == null || character.getKind() == CharacterKind.CHILD
                || characterId == null || characterId.isBlank() || "child".equalsIgnoreCase(characterId))
                ? StorybookKeys.photo(bookId)
                : StorybookKeys.characterPhoto(bookId, character.getCharacterId());

        store.put(key, vault.encrypt(clean), "application/octet-stream");

        if (character != null) {
            character.setPhotoKey(key);
            character.setPhotoConsentAt(Instant.now());
            characters.save(character);
        }
    }

    @Transactional
    public void attachPhotos(
            Long bookId,
            CreateStorybookRequest request,
            MultipartFile childPhoto,
            List<MultipartFile> characterPhotos,
            Boolean explicitConsent
    ) {
        attachPhotos(bookId, request, childPhoto, null, characterPhotos, explicitConsent);
    }

    @Transactional
    public void attachPhotos(
            Long bookId,
            CreateStorybookRequest request,
            MultipartFile childPhoto,
            MultipartFile companionPhoto,
            List<MultipartFile> characterPhotos,
            Boolean explicitConsent
    ) {
        if (request == null) {
            return;
        }

        boolean consent = explicitConsent != null
                ? explicitConsent
                : (request.photoConsent() == null || request.photoConsent());

        Set<String> photoAttachedCharacters = new HashSet<>();

        // 1. Process child photo from multipart
        if (childPhoto != null && !childPhoto.isEmpty()) {
            attachPhoto(bookId, "child", childPhoto, consent);
            photoAttachedCharacters.add("child");
        }

        // 2. Process companion photo from multipart
        if (companionPhoto != null && !companionPhoto.isEmpty()) {
            attachPhoto(bookId, "companion", companionPhoto, consent);
            photoAttachedCharacters.add("companion");
        }

        // 3. Process character photos from multipart files
        if (characterPhotos != null && !characterPhotos.isEmpty()) {
            List<CharacterInput> requestedChars = request.characters() != null ? request.characters() : List.of();
            Set<Integer> matchedCharIndices = new HashSet<>();

            for (MultipartFile file : characterPhotos) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String originalFilename = file.getOriginalFilename();
                String baseName = originalFilename != null
                        ? originalFilename.replaceAll("\\.[^.]+$", "").trim().toLowerCase()
                        : "";

                // Match companion by filename if companion exists and not yet attached
                if (request.companion() != null && !photoAttachedCharacters.contains("companion")) {
                    String compName = request.companion().nameAr() != null ? request.companion().nameAr().toLowerCase() : "";
                    if (baseName.contains("companion") || baseName.contains("pet") || (!compName.isEmpty() && baseName.contains(compName))) {
                        attachPhoto(bookId, "companion", file, consent);
                        photoAttachedCharacters.add("companion");
                        continue;
                    }
                }

                int matchIndex = -1;
                for (int i = 0; i < requestedChars.size(); i++) {
                    if (matchedCharIndices.contains(i)) {
                        continue;
                    }
                    CharacterInput ci = requestedChars.get(i);
                    String cid = ci.id() != null ? ci.id().toLowerCase() : "";
                    String cname = ci.name() != null ? ci.name().toLowerCase() : "";
                    if ((!cid.isEmpty() && baseName.contains(cid)) || (!cname.isEmpty() && baseName.contains(cname))) {
                        matchIndex = i;
                        break;
                    }
                }

                if (matchIndex == -1) {
                    for (int i = 0; i < requestedChars.size(); i++) {
                        if (!matchedCharIndices.contains(i)) {
                            matchIndex = i;
                            break;
                        }
                    }
                }

                if (matchIndex != -1) {
                    matchedCharIndices.add(matchIndex);
                    CharacterInput matched = requestedChars.get(matchIndex);
                    String targetId = matched.id() != null ? matched.id() : matched.name();
                    attachPhoto(bookId, targetId, file, consent);
                    if (targetId != null) {
                        photoAttachedCharacters.add(targetId.toLowerCase());
                    }
                } else if (!photoAttachedCharacters.contains("child")) {
                    attachPhoto(bookId, "child", file, consent);
                    photoAttachedCharacters.add("child");
                }
            }
        }

        // 4. Process child photo from Base64 (if not already attached via multipart)
        if (!photoAttachedCharacters.contains("child")) {
            if (request.childPhotoBase64() != null && !request.childPhotoBase64().isBlank()) {
                attachPhotoBase64(bookId, "child", request.childPhotoBase64(), consent);
                photoAttachedCharacters.add("child");
            } else if (request.child() != null && request.child().photoBase64() != null && !request.child().photoBase64().isBlank()) {
                attachPhotoBase64(bookId, "child", request.child().photoBase64(), consent);
                photoAttachedCharacters.add("child");
            }
        }

        // 5. Process companion photo from Base64 (if not already attached via multipart)
        if (!photoAttachedCharacters.contains("companion")) {
            if (request.companionPhotoBase64() != null && !request.companionPhotoBase64().isBlank()) {
                attachPhotoBase64(bookId, "companion", request.companionPhotoBase64(), consent);
                photoAttachedCharacters.add("companion");
            } else if (request.companion() != null && request.companion().photoBase64() != null && !request.companion().photoBase64().isBlank()) {
                attachPhotoBase64(bookId, "companion", request.companion().photoBase64(), consent);
                photoAttachedCharacters.add("companion");
            }
        }

        // 6. Process character photos from Base64 (if not already attached via multipart)
        if (request.characters() != null) {
            for (CharacterInput ci : request.characters()) {
                String targetId = ci.id() != null ? ci.id() : ci.name();
                String targetKey = targetId != null ? targetId.toLowerCase() : "";
                if (!targetKey.isEmpty() && !photoAttachedCharacters.contains(targetKey) && ci.photoBase64() != null && !ci.photoBase64().isBlank()) {
                    attachPhotoBase64(bookId, targetId, ci.photoBase64(), consent);
                    photoAttachedCharacters.add(targetKey);
                }
            }
        }
    }
}
