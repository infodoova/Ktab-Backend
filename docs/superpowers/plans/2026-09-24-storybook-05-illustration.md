# Storybook 05 — Illustration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Read `2026-09-24-storybook-00-overview.md` first — its "Shared contracts" and "Global Constraints" sections apply to every task here.

**Goal:** From an approved story, generate the character sheet, let the parent approve the look (approval gate 2), then illustrate the cover and every page with reference images, check each image with visual QA, retry failures (switching to the fallback model), and move the book to `RENDERING` — or to `QA` for admin review when a page cannot pass.

**Architecture:** Four step handlers (`CHARACTER_SHEET`, `PURGE_PHOTO`, `ILLUSTRATE_PAGE`, `QA_PAGE`) plug into the orchestrator. Images go to R2 under **deterministic keys** through a new `StorybookAssetStore`, so a re-run after a crash finds what was already paid for. All provider calls happen outside transactions; `IllustrationPersistence` holds the short transactions, and book-level advancement takes a row lock on the book so parallel page jobs cannot race. The optional photo is re-encoded to JPEG (dropping EXIF), encrypted with AES-256-GCM, and deleted right after the sheet is generated from it.

**Tech Stack:** Spring Boot 3.5.7, AWS SDK v2 `S3Client` (already configured for R2 in Ktab), `javax.crypto` AES-GCM, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md`

## Global Constraints

See the overview. Most relevant here:

- Images: 2K square, text-free, empty TOP/BOTTOM zone. Each page call gets the CHILD sheet, the style reference and (if in the scene) the COMPANION sheet.
- Visual QA failure → retried up to 3 times, then flagged for manual review. Attempts 1–2 in a round use Nano Banana 2, attempts 3–4 Nano Banana Pro (D6).
- Every step idempotent: a retry never charges twice.
- Photo: explicit consent, encrypted while stored, deleted right after the character sheet is made (D7), never used for training.
- Look regenerations before approval: max 2. Final-review page regenerations: max 3 per book (D9).

## Review Focus

Owned by this sub-plan: **#3 the worker dies after the image provider returned a paid image but before the job was marked done.** Expected: the re-run finds the stored image (DB row, or the object at the deterministic key) and does not call the provider again. Test: Task 6, `PageIllustrationHandlerTest.doesNotRegenerateWhenVersionAlreadyStored` and `reusesAnUploadedObjectWithoutARow`.

The one window that cannot be closed: a crash between the provider returning bytes and the upload finishing. The provider API is not idempotent, so that attempt is paid again. The ledger row is written before the upload, so the double charge is at least visible in `tbl_storybook_ai_calls`.

Also here:
- **Several page QA jobs finish at the same moment.** Expected: the book advances to `RENDERING` exactly once and exactly one `RENDER_PDF` job exists. Test: Task 7, `IllustrationPersistenceIT.lastPassingPageAdvancesTheBookOnce`.
- **A photo with GPS EXIF data.** Expected: the stored photo is a re-encoded JPEG with no metadata. Test: Task 2, `PhotoIntakeServiceTest.reencodesTheUpload`.

## File structure

```
src/main/java/com/doova/ktab/features/storybook/
├── config/StorybookProperties.java               (Task 2: photo settings)
├── storage/StorybookKeys.java  StorybookAssetStore.java           (Task 1)
├── character/PhotoVault.java  PhotoIntakeService.java              (Task 2)
├── character/CharacterPrompts.java                                 (Task 4: add sheetFromPreviousSheet)
├── illustration/StyleReferences.java  ReferenceAssembler.java  ModelSelector.java (Task 3)
├── illustration/IllustrationPersistence.java  SheetContext.java  PageContext.java  QaContext.java (Tasks 4, 6, 7)
├── illustration/CharacterSheetHandler.java  PhotoPurgeHandler.java (Task 4)
├── illustration/LookService.java                                   (Task 5)
├── illustration/PageIllustrationHandler.java                       (Task 6)
├── illustration/PageQaHandler.java                                 (Task 7)
├── illustration/PageRegenerationService.java                       (Task 8)
└── web/StorybookController.java                                    (Tasks 2, 5, 8: endpoints)
```

---

### Task 1: Deterministic-key asset store

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/storage/StorybookKeys.java`, `StorybookAssetStore.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/storage/StorybookKeysTest.java`, `StorybookAssetStoreTest.java`

**Interfaces:**
- Consumes: Ktab's `software.amazon.awssdk.services.s3.S3Client` bean and the `cloudflare.r2.bucketName` / `aws.s3.bucketName` property (same as `service/file/impl/S3Service`).
- Produces:
  - `StorybookKeys.characterSheet(Long bookId, CharacterKind kind, int version)` → `storybook/<bookId>/characters/<kind>/v<version>.png` (kind lower-case)
  - `StorybookKeys.pageImage(Long bookId, int pageIndex, int generation)` → `storybook/<bookId>/pages/<pageIndex>/g<generation>.png`
  - `StorybookKeys.photo(Long bookId)` → `storybook/<bookId>/photo/source.enc`
  - `StorybookKeys.pdf(Long bookId, int renderRound)` → `storybook/<bookId>/book-r<renderRound>.pdf`
  - `StorybookAssetStore`: `void put(String key, byte[] bytes, String contentType)`, `byte[] get(String key)`, `boolean exists(String key)`, `void delete(String key)` (idempotent).

Why not `FileStorageService.storeBytes`: it names every object with a random UUID, so a retried step cannot find what it already uploaded.

- [ ] **Step 1: Write the failing tests**

`StorybookKeysTest.java`:
```java
package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookKeysTest {

    @Test
    void keysAreDeterministic() {
        assertThat(StorybookKeys.characterSheet(7L, CharacterKind.CHILD, 2)).isEqualTo("storybook/7/characters/child/v2.png");
        assertThat(StorybookKeys.pageImage(7L, 0, 3)).isEqualTo("storybook/7/pages/0/g3.png");
        assertThat(StorybookKeys.photo(7L)).isEqualTo("storybook/7/photo/source.enc");
        assertThat(StorybookKeys.pdf(7L, 1)).isEqualTo("storybook/7/book-r1.pdf");
    }
}
```

`StorybookAssetStoreTest.java`:
```java
package com.doova.ktab.features.storybook.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorybookAssetStoreTest {

    private final S3Client s3 = mock(S3Client.class);
    private final StorybookAssetStore store = new StorybookAssetStore(s3, "ktab-bucket");

    @Test
    void putWritesToTheExactKey() {
        store.put("storybook/1/pages/1/g1.png", new byte[]{1, 2, 3}, "image/png");

        ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(req.capture(), any(RequestBody.class));
        assertThat(req.getValue().bucket()).isEqualTo("ktab-bucket");
        assertThat(req.getValue().key()).isEqualTo("storybook/1/pages/1/g1.png");
        assertThat(req.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void existsIsFalseOn404() {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());
        assertThat(store.exists("missing")).isFalse();
    }

    @Test
    void existsIsTrueWhenHeadSucceeds() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        assertThat(store.exists("present")).isTrue();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='StorybookKeysTest,StorybookAssetStoreTest'`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`StorybookKeys.java`:
```java
package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.enums.CharacterKind;

import java.util.Locale;

public final class StorybookKeys {

    private StorybookKeys() {
    }

    public static String characterSheet(Long bookId, CharacterKind kind, int version) {
        return "storybook/" + bookId + "/characters/" + kind.name().toLowerCase(Locale.ROOT) + "/v" + version + ".png";
    }

    public static String pageImage(Long bookId, int pageIndex, int generation) {
        return "storybook/" + bookId + "/pages/" + pageIndex + "/g" + generation + ".png";
    }

    public static String photo(Long bookId) {
        return "storybook/" + bookId + "/photo/source.enc";
    }

    public static String pdf(Long bookId, int renderRound) {
        return "storybook/" + bookId + "/book-r" + renderRound + ".pdf";
    }
}
```

`StorybookAssetStore.java`:
```java
package com.doova.ktab.features.storybook.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** R2 access with caller-chosen keys, so retried steps can find what they already uploaded. */
@Component
public class StorybookAssetStore {

    private final S3Client s3;
    private final String bucket;

    @Autowired
    public StorybookAssetStore(S3Client s3, @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName}}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    public byte[] get(String key) {
        ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
        return bytes.asByteArray();
    }

    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./mvnw -q test -Dtest='StorybookKeysTest,StorybookAssetStoreTest'`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/storage src/test/java/com/doova/ktab/features/storybook/storage
git commit -m "feat(storybook): add deterministic-key R2 asset store"
```

---

### Task 2: Photo vault and photo intake

**Files:**
- Modify: `src/main/java/com/doova/ktab/features/storybook/config/StorybookProperties.java` (add `Photo` group)
- Modify: `src/main/resources/application.properties`
- Create: `src/main/java/com/doova/ktab/features/storybook/character/PhotoVault.java`, `PhotoIntakeService.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java` (photo endpoint)
- Test: `src/test/java/com/doova/ktab/features/storybook/character/PhotoVaultTest.java`, `PhotoIntakeServiceTest.java`

**Interfaces:**
- Consumes: `StorybookAssetStore`, `StorybookKeys` (Task 1); `ImageDownscaler` (01); `StorybookAccessGuard`, `StorybookCharacterRepository`, `CharacterKind`, `ApiMessageKey` keys (02).
- Produces:
  - `StorybookProperties.Photo` with `encryptionKey` (Base64 of 32 bytes; env `KTAB_STORYBOOK_PHOTO_KEY`) and `maxBytes` (default 10 MB); accessor `getPhoto()`.
  - `PhotoVault.encrypt(byte[] plain) : byte[]` → `[12-byte IV][ciphertext + 16-byte tag]`; `decrypt(byte[] sealed) : byte[]` → throws `IllegalStateException` on tampering or a missing key.
  - `PhotoIntakeService.upload(User owner, Long bookId, MultipartFile photo, boolean consent)` — `@Transactional`; requires `consent == true` (else 400 `STORYBOOK_PHOTO_CONSENT_REQUIRED`); book `DRAFT`, or `STORY_READY` with `storyApprovedAt == null` (else 409); JPEG/PNG only, ≤ `maxBytes`, must decode; re-encodes to JPEG with long side ≤ 2048 (drops EXIF), encrypts, stores at `StorybookKeys.photo(bookId)`, sets the CHILD character's `photoKey` and `photoConsentAt`.
  - Endpoint: `POST /storybook/books/{bookId}/photo` (multipart `photo`, param `consent`) → 202.

- [ ] **Step 1: Write the failing tests**

`PhotoVaultTest.java`:
```java
package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoVaultTest {

    static StorybookProperties withKey() {
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
```

`PhotoIntakeServiceTest.java`:
```java
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
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='PhotoVaultTest,PhotoIntakeServiceTest'`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Add the properties**

In `StorybookProperties`, add the field `private Photo photo = new Photo();` and:
```java
    @Getter
    @Setter
    public static class Photo {
        /** Base64 of 32 random bytes (AES-256). Env KTAB_STORYBOOK_PHOTO_KEY. Never commit it. */
        private String encryptionKey;
        private long maxBytes = 10L * 1024 * 1024;
    }
```

Append to `application.properties`:
```properties
ktab.storybook.photo.encryption-key=${KTAB_STORYBOOK_PHOTO_KEY:}
```

Generate a key for each environment with `openssl rand -base64 32`.

- [ ] **Step 4: Implement `PhotoVault`**

```java
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
```

- [ ] **Step 5: Implement `PhotoIntakeService`**

```java
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
```

`ImageDownscaler.toJpeg` throws `IllegalArgumentException("Not a readable image")` for non-images (sub-plan 01), which is mapped to 400 here.

- [ ] **Step 6: Add the endpoint**

In `StorybookController`, add the field `private final com.doova.ktab.features.storybook.character.PhotoIntakeService photoIntakeService;` (after `storyApprovalService`) and:
```java
    @PostMapping(path = "/books/{bookId}/photo", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> uploadPhoto(@CurrentUser User user, @PathVariable Long bookId,
                                                         @RequestParam("photo") org.springframework.web.multipart.MultipartFile photo,
                                                         @RequestParam("consent") boolean consent) {
        photoIntakeService.upload(user, bookId, photo, consent);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }
```
Add `mock(PhotoIntakeService.class)` as the next argument in `StorybookControllerTest.setUp()`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='PhotoVaultTest,PhotoIntakeServiceTest,StorybookControllerTest'`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/main/resources/application.properties src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): add consented, encrypted, EXIF-free photo intake"
```

---

### Task 3: Style references, reference assembly and model selection

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/illustration/StyleReferences.java`, `ReferenceAssembler.java`, `ModelSelector.java`
- Create: `src/test/resources/storybook/styles/soft_watercolor.png` (test-only placeholder: any small valid PNG, e.g. generated in Step 1)
- Test: `src/test/java/com/doova/ktab/features/storybook/illustration/ReferenceAssemblerTest.java`, `ModelSelectorTest.java`, `StyleReferencesTest.java`

**Interfaces:**
- Consumes: `ArtStyle.referenceResource()` (02), `ReferenceImage` (01), `CharacterInScene` (01), `StorybookProperties.getImage()` (01).
- Produces:
  - `StyleReferences.get(ArtStyle style) : byte[]` — cached classpath read; `IllegalStateException` naming the missing file. A `@PostConstruct`-free design: checked on first use and by `StyleReferencesTest`.
  - `ReferenceAssembler.forPage(byte[] childSheet, byte[] styleRef, byte[] companionSheet, List<CharacterInScene> cast) : List<ReferenceImage>` → `[child, style]` plus the companion sheet only when `companionSheet != null` and `cast` has ref `COMPANION`. Order matches `VisualQa`'s expectations (sub-plan 01).
  - `ModelSelector.modelFor(int attemptInRound) : String` → primary for attempts `1..primaryGenerations`, fallback after.

- [ ] **Step 1: Create the test-only style PNG**

```bash
mkdir -p src/test/resources/storybook/styles
echo 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==' \
  | base64 -d > src/test/resources/storybook/styles/soft_watercolor.png
```
(A 1×1 PNG. Maven puts `target/test-classes` before `target/classes`, so tests always load this file; production loads the art director's file from `src/main/resources/storybook/styles/`.)

- [ ] **Step 2: Write the failing tests**

`ReferenceAssemblerTest.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.story.CharacterInScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceAssemblerTest {

    private final byte[] child = {1}, style = {2}, companion = {3};

    @Test
    void childAndStyleAlwaysComeFirst() {
        var refs = ReferenceAssembler.forPage(child, style, companion, List.of(new CharacterInScene("CHILD", "happy")));
        assertThat(refs).extracting(r -> r.bytes()[0]).containsExactly((byte) 1, (byte) 2);
    }

    @Test
    void companionIsAddedOnlyWhenInTheScene() {
        var refs = ReferenceAssembler.forPage(child, style, companion,
                List.of(new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "curious")));
        assertThat(refs).extracting(r -> r.bytes()[0]).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void noCompanionSheetMeansNoCompanionReference() {
        var refs = ReferenceAssembler.forPage(child, style, null, List.of(new CharacterInScene("COMPANION", "x")));
        assertThat(refs).hasSize(2);
    }
}
```

`ModelSelectorTest.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSelectorTest {

    private final ModelSelector selector = new ModelSelector(new StorybookProperties());

    @Test
    void firstTwoAttemptsUseNanoBanana2ThenPro() {
        assertThat(selector.modelFor(1)).isEqualTo("gemini-3.1-flash-image");
        assertThat(selector.modelFor(2)).isEqualTo("gemini-3.1-flash-image");
        assertThat(selector.modelFor(3)).isEqualTo("gemini-3-pro-image");
        assertThat(selector.modelFor(4)).isEqualTo("gemini-3-pro-image");
    }
}
```

`StyleReferencesTest.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StyleReferencesTest {

    @Test
    void everyArtStyleHasAReferenceImage() {
        StyleReferences refs = new StyleReferences();
        for (ArtStyle style : ArtStyle.values()) {
            assertThat(refs.get(style)).isNotEmpty();
        }
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='ReferenceAssemblerTest,ModelSelectorTest,StyleReferencesTest'`
Expected: COMPILATION ERROR.

- [ ] **Step 4: Implement**

`StyleReferences.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

@Component
public class StyleReferences {

    private final Map<ArtStyle, byte[]> cache = new EnumMap<>(ArtStyle.class);

    public synchronized byte[] get(ArtStyle style) {
        return cache.computeIfAbsent(style, s -> {
            ClassPathResource resource = new ClassPathResource(s.referenceResource());
            if (!resource.exists()) {
                throw new IllegalStateException("Missing style reference " + s.referenceResource()
                        + " (art-director deliverable, see the storybook overview plan)");
            }
            try (InputStream in = resource.getInputStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + s.referenceResource(), e);
            }
        });
    }
}
```

`ReferenceAssembler.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.story.CharacterInScene;

import java.util.ArrayList;
import java.util.List;

public final class ReferenceAssembler {

    private ReferenceAssembler() {
    }

    /** Order is CHILD sheet, style reference, then COMPANION sheet — VisualQa relies on it. */
    public static List<ReferenceImage> forPage(byte[] childSheet, byte[] styleRef, byte[] companionSheet,
                                               List<CharacterInScene> cast) {
        List<ReferenceImage> refs = new ArrayList<>();
        refs.add(new ReferenceImage(childSheet, "image/png"));
        refs.add(new ReferenceImage(styleRef, "image/png"));
        boolean companionInScene = cast != null && cast.stream().anyMatch(c -> "COMPANION".equals(c.ref()));
        if (companionSheet != null && companionInScene) {
            refs.add(new ReferenceImage(companionSheet, "image/png"));
        }
        return refs;
    }
}
```

`ModelSelector.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Decision D6: Nano Banana 2 first, Nano Banana Pro for pages that keep failing QA. */
@Component
@RequiredArgsConstructor
public class ModelSelector {

    private final StorybookProperties properties;

    public String modelFor(int attemptInRound) {
        StorybookProperties.Image cfg = properties.getImage();
        return attemptInRound <= cfg.getPrimaryGenerations() ? cfg.getPrimaryModel() : cfg.getFallbackModel();
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='ReferenceAssemblerTest,ModelSelectorTest,StyleReferencesTest'`
Expected: 5 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/illustration src/test/java/com/doova/ktab/features/storybook/illustration src/test/resources/storybook/styles
git commit -m "feat(storybook): add style references, reference ordering and model selection"
```

---

### Task 4: Character sheet and photo purge handlers

**Files:**
- Modify: `src/main/java/com/doova/ktab/features/storybook/character/CharacterPrompts.java` (add `sheetFromPreviousSheet`)
- Create: `src/main/java/com/doova/ktab/features/storybook/illustration/SheetContext.java`, `IllustrationPersistence.java`, `CharacterSheetHandler.java`, `PhotoPurgeHandler.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/illustration/CharacterSheetHandlerTest.java`, `PhotoPurgeHandlerTest.java`, `IllustrationPersistenceSheetIT.java`

**Interfaces:**
- Consumes: `ImageProvider`, `ImageRequest`, `ImageResult`, `ReferenceImage`, `CharacterPrompts` (01); `AiCallLedger`, repositories, entities (02); `JobEnqueuer`, `StorybookStateMachine`, `StepHandler`, `StepOutcome` (03); `StorybookAssetStore`, `StorybookKeys`, `PhotoVault`, `StyleReferences`, `ModelSelector` (Tasks 1–3).
- Produces:
  - `CharacterPrompts.sheetFromPreviousSheet(ChildGender g, AgeBand band)` — a new take on the character in the first reference (used when a photo-based character needs another look after the photo was purged, D7).
  - `record SheetContext(Long bookId, StorybookStatus status, boolean storyApproved, ChildGender gender, AgeBand ageBand, ChildAppearance appearance, CompanionSpec companion, ArtStyle style, int childSheetVersion, String childSheetKey, String photoKey, boolean photoBased, boolean companionSheetExists)`.
  - `IllustrationPersistence` (sheet part, each `@Transactional`): `SheetContext sheetContext(Long bookId)`, `void saveSheets(Long bookId, int version, String childKey, String companionKey, boolean photoUsed)` (sets keys/versions/`GENERATED`; enqueues `PURGE_PHOTO(-1, version)` when `photoUsed`; `STORY_READY → CHARACTER_READY` when still `STORY_READY`), `String photoKey(Long bookId)`, `void markPhotoPurged(Long bookId)`.
  - `CharacterSheetHandler` (`CHARACTER_SHEET`, generation = sheet version):
    1. Stale unless (`STORY_READY` and story approved) or `CHARACTER_READY` → `success()`.
    2. `childSheetVersion >= generation` → `success()` (already done).
    3. Child sheet: reuse the object at `characterSheet(book, CHILD, v)` if it exists; otherwise generate — photo present → `sheetFromPhoto` with refs `[photo, style]`; photo-based but purged and a previous sheet exists → `sheetFromPreviousSheet` with refs `[previousSheet, style]`; else `sheet(gender, band, appearance)` with refs `[style]` — record cost as `IMAGE_CHARACTER_SHEET`, upload.
    4. Companion sheet once (`v = 1`) if the book has a companion and no sheet yet (`IMAGE_COMPANION_SHEET`).
    5. `saveSheets(...)`.
  - `PhotoPurgeHandler` (`PURGE_PHOTO`): no photo key → `success()`; else `store.delete(key)` then `markPhotoPurged`.

- [ ] **Step 1: Add the prompt**

In `CharacterPrompts` (sub-plan 01), add:
```java
    public static String sheetFromPreviousSheet(ChildGender gender, AgeBand band) {
        return "Create a new version of the character in the first reference image: the same child, keeping their "
                + "face shape, skin tone, hair, eye colour, glasses and headwear, but with a fresh pose and outfit. "
                + "A " + gender.en() + " aged about " + mid(band) + ". Show the character twice side by side on a plain "
                + "white background: front view on the left, three-quarter view on the right, full body, standing, gentle "
                + "smile, identical outfit in both views. " + MODEST + " " + STYLE + " " + NO_TEXT;
    }
```

- [ ] **Step 2: Write the failing tests**

`CharacterSheetHandlerTest.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.PhotoVault;
import com.doova.ktab.features.storybook.character.PhotoVaultTest;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.image.ImageProvider;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CharacterSheetHandlerTest {

    private final ImageProvider images = mock(ImageProvider.class);
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final IllustrationPersistence persistence = mock(IllustrationPersistence.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final PhotoVault vault = new PhotoVault(PhotoVaultTest.withKey());
    private final CharacterSheetHandler handler = new CharacterSheetHandler(images, store, persistence, ledger, vault,
            new StyleReferences(), new ModelSelector(new StorybookProperties()));

    private static StorybookJob job(int version) {
        StorybookJob j = new StorybookJob();
        j.setId(3L);
        j.setStorybookId(9L);
        j.setStep(JobStep.CHARACTER_SHEET);
        j.setGeneration(version);
        return j;
    }

    private static SheetContext ctx(int childVersion, String photoKey, boolean photoBased, boolean companion) {
        return new SheetContext(9L, StorybookStatus.STORY_READY, true, ChildGender.GIRL, AgeBand.AGE_6_8,
                StoryFixtures.APPEARANCE,
                companion ? new com.doova.ktab.features.storybook.character.CompanionSpec(
                        com.doova.ktab.features.storybook.character.CompanionSpec.CompanionType.CAT, "بسبوسة", null,
                        com.doova.ktab.features.storybook.character.CompanionSpec.PetColor.ORANGE) : null,
                ArtStyle.SOFT_WATERCOLOR, childVersion, childVersion == 0 ? null : "old-sheet", photoKey, photoBased, false);
    }

    @Test
    void generatesFromAttributesWhenThereIsNoPhoto() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, false));
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "gemini-3.1-flash-image", 5));

        assertThat(handler.handle(job(1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references()).hasSize(1); // style only
        assertThat(req.getValue().prompt()).contains("girl").contains("front view");
        verify(store).put(eq("storybook/9/characters/child/v1.png"), any(), eq("image/png"));
        verify(ledger).recordImage(eq(9L), eq(3L), eq("IMAGE_CHARACTER_SHEET"), any());
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png", null, false);
    }

    @Test
    void usesThePhotoAndAsksForItsPurge() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, "storybook/9/photo/source.enc", true, false));
        when(store.get("storybook/9/photo/source.enc")).thenReturn(vault.encrypt(new byte[]{7}));
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references().get(0).bytes()).containsExactly(7);
        assertThat(req.getValue().prompt()).contains("photo reference");
        verify(persistence).saveSheets(eq(9L), eq(1), anyString(), isNull(), eq(true));
    }

    @Test
    void afterThePhotoIsGoneANewLookStartsFromThePreviousSheet() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(1, null, true, false));
        when(store.get("old-sheet")).thenReturn(new byte[]{5});
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(2));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references().get(0).bytes()).containsExactly(5);
        assertThat(req.getValue().prompt()).contains("new version of the character");
    }

    @Test
    void alsoDrawsTheCompanionOnce() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, true));
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(1));

        verify(images, times(2)).generate(any());
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png",
                "storybook/9/characters/companion/v1.png", false);
    }

    @Test
    void doneVersionsAreSkipped() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(1, null, false, false));
        assertThat(handler.handle(job(1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verifyNoInteractions(images, ledger);
    }

    @Test
    void anUploadedSheetIsReusedWithoutPayingAgain() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, false));
        when(store.exists("storybook/9/characters/child/v1.png")).thenReturn(true);

        handler.handle(job(1));

        verifyNoInteractions(images);
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png", null, false);
    }
}
```

`PhotoPurgeHandlerTest.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class PhotoPurgeHandlerTest {

    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final IllustrationPersistence persistence = mock(IllustrationPersistence.class);
    private final PhotoPurgeHandler handler = new PhotoPurgeHandler(store, persistence);

    private static StorybookJob job() {
        StorybookJob j = new StorybookJob();
        j.setStorybookId(9L);
        j.setStep(JobStep.PURGE_PHOTO);
        return j;
    }

    @Test
    void deletesThenMarks() {
        when(persistence.photoKey(9L)).thenReturn("storybook/9/photo/source.enc");
        handler.handle(job());
        var order = inOrder(store, persistence);
        order.verify(store).delete("storybook/9/photo/source.enc");
        order.verify(persistence).markPhotoPurged(9L);
    }

    @Test
    void nothingToPurgeIsFine() {
        when(persistence.photoKey(9L)).thenReturn(null);
        handler.handle(job());
        verifyNoInteractions(store);
    }
}
```

`IllustrationPersistenceSheetIT.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.features.storybook.web.StorybookDraftWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import({IllustrationPersistence.class, JobEnqueuer.class, StorybookStateMachine.class, ModelSelector.class,
        com.doova.ktab.features.storybook.config.StorybookProperties.class})
class IllustrationPersistenceSheetIT extends StorybookJpaIT {

    @Autowired IllustrationPersistence persistence;
    @Autowired StorybookRepository books;
    @Autowired StorybookCharacterRepository characters;
    @Autowired StorybookJobRepository jobs;

    private Storybook storyReadyBookWithCharacters() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "sheet-" + System.nanoTime() + "@example.com"));
        book.setStatus(StorybookStatus.STORY_READY);
        book.setStoryApprovedAt(java.time.Instant.now());
        var child = new com.doova.ktab.features.storybook.model.StorybookCharacter();
        child.setStorybook(book);
        child.setKind(CharacterKind.CHILD);
        child.setAttributes(com.doova.ktab.features.storybook.model.CharacterAttributes.ofChild(book.getInputs().appearance()));
        child.setPhotoKey("storybook/" + book.getId() + "/photo/source.enc");
        child.setPhotoConsentAt(java.time.Instant.now());
        em.persist(child);
        em.flush();
        return book;
    }

    @Test
    void savingSheetsReadiesTheCharacterAndSchedulesThePhotoPurge() {
        Storybook book = storyReadyBookWithCharacters();

        persistence.saveSheets(book.getId(), 1, "k-child", null, true);
        em.flush();
        em.clear();

        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.CHARACTER_READY);
        var child = characters.findByStorybook_IdAndKind(book.getId(), CharacterKind.CHILD).orElseThrow();
        assertThat(child.getSheetKey()).isEqualTo("k-child");
        assertThat(child.getSheetVersion()).isEqualTo(1);
        assertThat(child.getSheetStatus()).isEqualTo(CharacterSheetStatus.GENERATED);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getGeneration())
                .containsExactly(JobStep.PURGE_PHOTO + ":1");
    }

    @Test
    void purgeClearsTheKey() {
        Storybook book = storyReadyBookWithCharacters();
        persistence.markPhotoPurged(book.getId());
        em.flush();
        em.clear();

        var child = characters.findByStorybook_IdAndKind(book.getId(), CharacterKind.CHILD).orElseThrow();
        assertThat(child.getPhotoKey()).isNull();
        assertThat(child.getPhotoPurgedAt()).isNotNull();
        assertThat(persistence.photoKey(book.getId())).isNull();
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='CharacterSheetHandlerTest,PhotoPurgeHandlerTest,IllustrationPersistenceSheetIT'`
Expected: COMPILATION ERROR.

- [ ] **Step 4: Implement `SheetContext` and the sheet part of `IllustrationPersistence`**

`SheetContext.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.StorybookStatus;

public record SheetContext(Long bookId, StorybookStatus status, boolean storyApproved, ChildGender gender,
                           AgeBand ageBand, ChildAppearance appearance, CompanionSpec companion, ArtStyle style,
                           int childSheetVersion, String childSheetKey, String photoKey, boolean photoBased,
                           boolean companionSheetExists) {
}
```

`IllustrationPersistence.java` (Tasks 6 and 7 add more methods to this class):
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.CharacterSheetStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IllustrationPersistence {

    private final StorybookRepository books;
    private final StorybookCharacterRepository characters;
    private final StorybookPageRepository pages;
    private final StorybookPageImageRepository images;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final com.doova.ktab.features.storybook.config.StorybookProperties properties;

    @Transactional(readOnly = true)
    public SheetContext sheetContext(Long bookId) {
        Storybook book = books.findById(bookId).orElseThrow();
        StorybookCharacter child = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElseThrow();
        Optional<StorybookCharacter> companion = characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION);
        return new SheetContext(bookId, book.getStatus(), book.getStoryApprovedAt() != null,
                book.getInputs().gender(), book.getInputs().ageBand(), book.getInputs().appearance(),
                book.getInputs().companion(), book.getStyle(), child.getSheetVersion(), child.getSheetKey(),
                child.getPhotoKey(), child.getPhotoConsentAt() != null,
                companion.map(c -> c.getSheetKey() != null).orElse(false));
    }

    @Transactional
    public void saveSheets(Long bookId, int version, String childKey, String companionKey, boolean photoUsed) {
        StorybookCharacter child = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElseThrow();
        child.setSheetKey(childKey);
        child.setSheetVersion(version);
        child.setSheetStatus(CharacterSheetStatus.GENERATED);
        if (companionKey != null) {
            characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION).ifPresent(c -> {
                c.setSheetKey(companionKey);
                c.setSheetVersion(1);
                c.setSheetStatus(CharacterSheetStatus.GENERATED);
            });
        }
        if (photoUsed) {
            enqueuer.enqueue(bookId, JobStep.PURGE_PHOTO, -1, version);
        }
        Storybook book = books.findById(bookId).orElseThrow();
        if (book.getStatus() == StorybookStatus.STORY_READY) {
            stateMachine.transition(book, StorybookStatus.CHARACTER_READY);
        }
    }

    @Transactional(readOnly = true)
    public String photoKey(Long bookId) {
        return characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).map(StorybookCharacter::getPhotoKey).orElse(null);
    }

    @Transactional
    public void markPhotoPurged(Long bookId) {
        characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).ifPresent(c -> {
            c.setPhotoKey(null);
            c.setPhotoPurgedAt(Instant.now());
        });
    }
}
```

`StorybookEntityFixtures.newBook` (sub-plan 02) creates the book only; the IT above adds the CHILD character itself.

- [ ] **Step 5: Implement the handlers**

`CharacterSheetHandler.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.CharacterPrompts;
import com.doova.ktab.features.storybook.character.PhotoVault;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.image.ImageProvider;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CharacterSheetHandler implements StepHandler {

    private final ImageProvider images;
    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;
    private final AiCallLedger ledger;
    private final PhotoVault vault;
    private final StyleReferences styles;
    private final ModelSelector models;

    @Override
    public JobStep step() {
        return JobStep.CHARACTER_SHEET;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        SheetContext ctx = persistence.sheetContext(job.getStorybookId());
        boolean ready = (ctx.status() == StorybookStatus.STORY_READY && ctx.storyApproved())
                || ctx.status() == StorybookStatus.CHARACTER_READY;
        int version = job.getGeneration();
        if (!ready || ctx.childSheetVersion() >= version) {
            return StepOutcome.success();
        }
        byte[] style = styles.get(ctx.style());
        String model = models.modelFor(1);

        String childKey = StorybookKeys.characterSheet(ctx.bookId(), CharacterKind.CHILD, version);
        boolean photoUsed = false;
        if (!store.exists(childKey)) {
            ImageRequest request;
            if (ctx.photoKey() != null) {
                byte[] photo = vault.decrypt(store.get(ctx.photoKey()));
                request = new ImageRequest(model, CharacterPrompts.sheetFromPhoto(ctx.gender(), ctx.ageBand()),
                        List.of(new ReferenceImage(photo, "image/jpeg"), new ReferenceImage(style, "image/png")));
                photoUsed = true;
            } else if (ctx.photoBased() && ctx.childSheetKey() != null) {
                request = new ImageRequest(model, CharacterPrompts.sheetFromPreviousSheet(ctx.gender(), ctx.ageBand()),
                        List.of(new ReferenceImage(store.get(ctx.childSheetKey()), "image/png"), new ReferenceImage(style, "image/png")));
            } else {
                request = new ImageRequest(model, CharacterPrompts.sheet(ctx.gender(), ctx.ageBand(), ctx.appearance()),
                        List.of(new ReferenceImage(style, "image/png")));
            }
            ImageResult result = images.generate(request);
            ledger.recordImage(ctx.bookId(), job.getId(), "IMAGE_CHARACTER_SHEET", result);
            store.put(childKey, result.bytes(), result.mimeType());
        } else if (ctx.photoKey() != null) {
            photoUsed = true; // sheet was made from the photo before a crash; still purge it
        }

        String companionKey = null;
        if (ctx.companion() != null && !ctx.companionSheetExists()) {
            companionKey = StorybookKeys.characterSheet(ctx.bookId(), CharacterKind.COMPANION, 1);
            if (!store.exists(companionKey)) {
                ImageResult result = images.generate(new ImageRequest(model,
                        CharacterPrompts.companionSheet(ctx.companion()), List.of(new ReferenceImage(style, "image/png"))));
                ledger.recordImage(ctx.bookId(), job.getId(), "IMAGE_COMPANION_SHEET", result);
                store.put(companionKey, result.bytes(), result.mimeType());
            }
        }

        persistence.saveSheets(ctx.bookId(), version, childKey, companionKey, photoUsed);
        return StepOutcome.success();
    }
}
```

`PhotoPurgeHandler.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Spec: the photo is deleted right after the character sheet is made (D7). */
@Component
@RequiredArgsConstructor
public class PhotoPurgeHandler implements StepHandler {

    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;

    @Override
    public JobStep step() {
        return JobStep.PURGE_PHOTO;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        String key = persistence.photoKey(job.getStorybookId());
        if (key != null) {
            store.delete(key);
            persistence.markPhotoPurged(job.getStorybookId());
        }
        return StepOutcome.success();
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='CharacterSheetHandlerTest,PhotoPurgeHandlerTest,IllustrationPersistenceSheetIT'`
Expected: 10 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook/illustration
git commit -m "feat(storybook): generate character sheets and purge the photo afterwards"
```

---

### Task 5: Look regeneration and approval (gate 2)

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/illustration/LookService.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/illustration/LookServiceIT.java`

**Interfaces:**
- Consumes: `StorybookAccessGuard`, repositories, `ApiMessageKey` (02); `JobEnqueuer`, `StorybookStateMachine` (03); `StorybookProperties.getLimits().getLookRegenerations()` (01).
- Produces:
  - `LookService.regenerate(User owner, Long bookId)` — `@Transactional`; `CHARACTER_READY`, look not approved, the current sheet is ready (`sheetStatus == GENERATED` and `sheetVersion == lookRegenerations + 1`) else 409; `lookRegenerations < limit` else 400 `STORYBOOK_LIMIT_REACHED`; `lookRegenerations++`; enqueue `CHARACTER_SHEET(-1, sheetVersion + 1)`.
  - `LookService.approve(User owner, Long bookId)` — `@Transactional`; same readiness check else 409; `lookApprovedAt = now`, CHILD `sheetStatus = APPROVED`, `CHARACTER_READY → ILLUSTRATING`; for every page (cover + story): `generation = 1`, `roundStartGeneration = 1`, enqueue `ILLUSTRATE_PAGE(pageIndex, 1)`.
  - Endpoints: `POST /storybook/books/{bookId}/character/regenerate`, `POST /storybook/books/{bookId}/character/approve` → 202.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.CharacterAttributes;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({LookService.class, StorybookAccessGuard.class, JobEnqueuer.class, StorybookStateMachine.class,
        StorybookProperties.class, StoryPersistence.class})
class LookServiceIT extends StorybookJpaIT {

    @Autowired LookService looks;
    @Autowired StoryPersistence stories;
    @Autowired StorybookRepository books;
    @Autowired StorybookJobRepository jobs;

    private User owner;

    private Storybook characterReadyBook() {
        owner = UserFixtures.reader(em, "look-" + System.nanoTime() + "@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0); // cover + 10 pages
        book.setStatus(StorybookStatus.CHARACTER_READY);
        book.setStoryApprovedAt(java.time.Instant.now());
        StorybookCharacter child = new StorybookCharacter();
        child.setStorybook(book);
        child.setKind(CharacterKind.CHILD);
        child.setAttributes(CharacterAttributes.ofChild(book.getInputs().appearance()));
        child.setSheetKey("k");
        child.setSheetVersion(1);
        child.setSheetStatus(CharacterSheetStatus.GENERATED);
        em.persist(child);
        em.flush();
        return book;
    }

    @Test
    void approvingStartsIllustrationForTheCoverAndEveryPage() {
        Storybook book = characterReadyBook();

        looks.approve(owner, book.getId());
        em.flush();
        em.clear();

        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .filteredOn(j -> j.getStep() == JobStep.ILLUSTRATE_PAGE)
                .extracting(j -> (int) j.getPageIndex())
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void regenerationIsCappedAndBlocksApprovalUntilTheNewSheetExists() {
        Storybook book = characterReadyBook();

        looks.regenerate(owner, book.getId());
        assertThatThrownBy(() -> looks.approve(owner, book.getId())).isInstanceOf(StorybookStateConflictException.class);
        assertThatThrownBy(() -> looks.regenerate(owner, book.getId())).isInstanceOf(StorybookStateConflictException.class);

        // the new sheet arrives
        var child = em.getEntityManager().createQuery(
                "select c from StorybookCharacter c where c.storybook.id = :id and c.kind = :k", StorybookCharacter.class)
                .setParameter("id", book.getId()).setParameter("k", CharacterKind.CHILD).getSingleResult();
        child.setSheetVersion(2);
        looks.regenerate(owner, book.getId());   // 2nd regeneration: allowed (limit 2)
        child.setSheetVersion(3);
        assertThatThrownBy(() -> looks.regenerate(owner, book.getId()))
                .isInstanceOf(BadRequestException.class).hasMessage("STORYBOOK_LIMIT_REACHED");
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .filteredOn(j -> j.getStep() == JobStep.CHARACTER_SHEET)
                .extracting(j -> j.getGeneration()).containsExactly(2, 3);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=LookServiceIT`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.CharacterSheetStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LookService {

    private final StorybookAccessGuard guard;
    private final StorybookCharacterRepository characters;
    private final StorybookPageRepository pages;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final StorybookProperties properties;

    @Transactional
    public void regenerate(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        StorybookCharacter child = requireSheetReady(book);
        if (book.getLookRegenerations() >= properties.getLimits().getLookRegenerations()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_LIMIT_REACHED);
        }
        book.setLookRegenerations(book.getLookRegenerations() + 1);
        enqueuer.enqueue(bookId, JobStep.CHARACTER_SHEET, -1, child.getSheetVersion() + 1);
    }

    /** Approval gate 2: page illustration (the bulk of the image cost) starts only after this. */
    @Transactional
    public void approve(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        StorybookCharacter child = requireSheetReady(book);
        book.setLookApprovedAt(Instant.now());
        child.setSheetStatus(CharacterSheetStatus.APPROVED);
        stateMachine.transition(book, StorybookStatus.ILLUSTRATING);
        for (StorybookPage page : pages.findByStorybook_IdOrderByPageIndexAsc(bookId)) {
            page.setGeneration(1);
            page.setRoundStartGeneration(1);
            enqueuer.enqueue(bookId, JobStep.ILLUSTRATE_PAGE, page.getPageIndex(), 1);
        }
    }

    private StorybookCharacter requireSheetReady(Storybook book) {
        StorybookCharacter child = characters.findByStorybook_IdAndKind(book.getId(), CharacterKind.CHILD).orElseThrow();
        boolean ready = book.getStatus() == StorybookStatus.CHARACTER_READY
                && book.getLookApprovedAt() == null
                && child.getSheetStatus() == CharacterSheetStatus.GENERATED
                && child.getSheetVersion() == book.getLookRegenerations() + 1;
        if (!ready) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        return child;
    }
}
```

- [ ] **Step 4: Add the endpoints**

In `StorybookController`, add the field `private final com.doova.ktab.features.storybook.illustration.LookService lookService;` (after `photoIntakeService`) and:
```java
    @PostMapping("/books/{bookId}/character/regenerate")
    public ResponseEntity<ApiResponse<Void>> regenerateLook(@CurrentUser User user, @PathVariable Long bookId) {
        lookService.regenerate(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/books/{bookId}/character/approve")
    public ResponseEntity<ApiResponse<Void>> approveLook(@CurrentUser User user, @PathVariable Long bookId) {
        lookService.approve(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }
```
Add `mock(LookService.class)` as the next argument in `StorybookControllerTest.setUp()`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='LookServiceIT,StorybookControllerTest'`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): add look regeneration and approval gate"
```

---

### Task 6: Page illustration handler

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/illustration/PageContext.java`, `PageIllustrationHandler.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/illustration/IllustrationPersistence.java` (page methods)
- Test: `src/test/java/com/doova/ktab/features/storybook/illustration/PageIllustrationHandlerTest.java`

**Interfaces:**
- Consumes: Tasks 1–4; `CharacterPrompts.scene/cover` (01); `PageKind`, `PageImageStatus` (02).
- Produces:
  - `record PageContext(Long bookId, StorybookStatus status, Long pageId, int pageIndex, PageKind kind, String sceneEn, TextZone textZone, List<CharacterInScene> cast, int pageGeneration, int roundStartGeneration, boolean imageRowExists, String childSheetKey, String companionSheetKey, ArtStyle style, boolean hijab)` — `imageRowExists` refers to the job's generation.
  - `IllustrationPersistence.pageContext(Long bookId, int pageIndex, int generation)` (`readOnly`), `ensureQaEnqueued(Long bookId, int pageIndex, int generation)`, `savePageImage(Long bookId, Long pageId, int pageIndex, int generation, String key, String model, BigDecimal cost)` — inserts the `GENERATED` image row if absent and enqueues `QA_PAGE(pageIndex, generation)`.
  - `PageIllustrationHandler` (`ILLUSTRATE_PAGE`):
    1. Not `ILLUSTRATING`, or `pageGeneration != job.generation` (superseded) → `success()`.
    2. `imageRowExists` → `ensureQaEnqueued` → `success()` (no provider call).
    3. `model = modelFor(generation − roundStart + 1)`; key = `pageImage(book, pageIndex, generation)`.
    4. Object already at key → reuse, cost 0 (the ledger already has it). Else refs via `ReferenceAssembler`, prompt `cover(...)` for the cover or `scene(...)`, generate, `recordImage(... "IMAGE_PAGE" ...)`, upload.
    5. `savePageImage(...)` → `success()`.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.image.ImageProvider;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PageIllustrationHandlerTest {

    private final ImageProvider images = mock(ImageProvider.class);
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final IllustrationPersistence persistence = mock(IllustrationPersistence.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final PageIllustrationHandler handler = new PageIllustrationHandler(images, store, persistence, ledger,
            new StyleReferences(), new ModelSelector(new StorybookProperties()));

    @BeforeEach
    void setUp() {
        when(store.get("child-sheet")).thenReturn(new byte[]{1});
        when(store.get("companion-sheet")).thenReturn(new byte[]{3});
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));
        when(ledger.recordImage(any(), any(), any(), any())).thenReturn(new BigDecimal("0.101"));
    }

    private static StorybookJob job(int pageIndex, int generation) {
        StorybookJob j = new StorybookJob();
        j.setId(11L);
        j.setStorybookId(9L);
        j.setStep(JobStep.ILLUSTRATE_PAGE);
        j.setPageIndex(pageIndex);
        j.setGeneration(generation);
        return j;
    }

    private static PageContext ctx(int pageIndex, int pageGeneration, int roundStart, boolean rowExists,
                                   StorybookStatus status, List<CharacterInScene> cast) {
        return new PageContext(9L, status, 100L, pageIndex, pageIndex == 0 ? PageKind.COVER : PageKind.STORY,
                "The CHILD waves.", TextZone.TOP, cast, pageGeneration, roundStart, rowExists,
                "child-sheet", "companion-sheet", ArtStyle.SOFT_WATERCOLOR, false);
    }

    @Test
    void illustratesAPageWithChildStyleAndCompanionReferences() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(ctx(3, 1, 1, false, StorybookStatus.ILLUSTRATING,
                List.of(new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "happy"))));

        assertThat(handler.handle(job(3, 1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().model()).isEqualTo("gemini-3.1-flash-image");
        assertThat(req.getValue().references()).hasSize(3);
        assertThat(req.getValue().prompt()).contains("The CHILD waves.").contains("top third");
        verify(store).put(eq("storybook/9/pages/3/g1.png"), any(), eq("image/png"));
        verify(persistence).savePageImage(9L, 100L, 3, 1, "storybook/9/pages/3/g1.png",
                "gemini-3.1-flash-image", new BigDecimal("0.101"));
    }

    @Test
    void thirdAttemptInARoundUsesTheFallbackModel() {
        when(persistence.pageContext(9L, 3, 3)).thenReturn(ctx(3, 3, 1, false, StorybookStatus.ILLUSTRATING, List.of()));
        handler.handle(job(3, 3));
        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().model()).isEqualTo("gemini-3-pro-image");
    }

    @Test
    void aParentRegenerationRoundStartsAgainOnThePrimaryModel() {
        when(persistence.pageContext(9L, 3, 5)).thenReturn(ctx(3, 5, 5, false, StorybookStatus.ILLUSTRATING, List.of()));
        handler.handle(job(3, 5));
        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().model()).isEqualTo("gemini-3.1-flash-image");
    }

    @Test
    void doesNotRegenerateWhenVersionAlreadyStored() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(ctx(3, 1, 1, true, StorybookStatus.ILLUSTRATING, List.of()));

        assertThat(handler.handle(job(3, 1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        verifyNoInteractions(images, ledger);
        verify(persistence).ensureQaEnqueued(9L, 3, 1);
    }

    @Test
    void reusesAnUploadedObjectWithoutARow() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(ctx(3, 1, 1, false, StorybookStatus.ILLUSTRATING, List.of()));
        when(store.exists("storybook/9/pages/3/g1.png")).thenReturn(true);

        handler.handle(job(3, 1));

        verifyNoInteractions(images, ledger);
        verify(persistence).savePageImage(9L, 100L, 3, 1, "storybook/9/pages/3/g1.png",
                "gemini-3.1-flash-image", BigDecimal.ZERO);
    }

    @Test
    void supersededGenerationsAndStaleBooksDoNothing() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(ctx(3, 2, 1, false, StorybookStatus.ILLUSTRATING, List.of()));
        handler.handle(job(3, 1));
        when(persistence.pageContext(9L, 4, 1)).thenReturn(ctx(4, 1, 1, false, StorybookStatus.FAILED, List.of()));
        handler.handle(job(4, 1));
        verifyNoInteractions(images);
    }

    @Test
    void theCoverUsesTheCoverPrompt() {
        when(persistence.pageContext(9L, 0, 1)).thenReturn(ctx(0, 1, 1, false, StorybookStatus.ILLUSTRATING,
                List.of(new CharacterInScene("CHILD", "happy"))));
        handler.handle(job(0, 1));
        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().prompt()).contains("book cover");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=PageIllustrationHandlerTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `PageContext` and the persistence methods**

`PageContext.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.story.CharacterInScene;

import java.util.List;

public record PageContext(Long bookId, StorybookStatus status, Long pageId, int pageIndex, PageKind kind,
                          String sceneEn, TextZone textZone, List<CharacterInScene> cast, int pageGeneration,
                          int roundStartGeneration, boolean imageRowExists, String childSheetKey,
                          String companionSheetKey, ArtStyle style, boolean hijab) {
}
```

Add to `IllustrationPersistence`:
```java
    @Transactional(readOnly = true)
    public PageContext pageContext(Long bookId, int pageIndex, int generation) {
        Storybook book = books.findById(bookId).orElseThrow();
        com.doova.ktab.features.storybook.model.StorybookPage page =
                pages.findByStorybook_IdAndPageIndex(bookId, pageIndex).orElseThrow();
        String childSheet = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD)
                .map(StorybookCharacter::getSheetKey).orElse(null);
        String companionSheet = characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION)
                .map(StorybookCharacter::getSheetKey).orElse(null);
        return new PageContext(bookId, book.getStatus(), page.getId(), page.getPageIndex(), page.getKind(),
                page.getSceneEn(), page.getTextZone(), page.getCharacters(), page.getGeneration(),
                page.getRoundStartGeneration(), images.findByPage_IdAndGeneration(page.getId(), generation).isPresent(),
                childSheet, companionSheet, book.getStyle(), book.getInputs().appearance().hijab());
    }

    @Transactional
    public void ensureQaEnqueued(Long bookId, int pageIndex, int generation) {
        enqueuer.enqueue(bookId, JobStep.QA_PAGE, pageIndex, generation);
    }

    @Transactional
    public void savePageImage(Long bookId, Long pageId, int pageIndex, int generation, String key, String model,
                              java.math.BigDecimal cost) {
        com.doova.ktab.features.storybook.model.StorybookPage page = pages.findById(pageId).orElseThrow();
        if (images.findByPage_IdAndGeneration(pageId, generation).isEmpty()) {
            com.doova.ktab.features.storybook.model.StorybookPageImage image = new com.doova.ktab.features.storybook.model.StorybookPageImage();
            image.setPage(page);
            image.setGeneration(generation);
            image.setImageKey(key);
            image.setModel(model);
            image.setStatus(com.doova.ktab.features.storybook.enums.PageImageStatus.GENERATED);
            image.setCostUsd(cost);
            images.save(image);
        }
        enqueuer.enqueue(bookId, JobStep.QA_PAGE, pageIndex, generation);
    }
```

- [ ] **Step 4: Implement the handler**

```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.CharacterPrompts;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.image.ImageProvider;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PageIllustrationHandler implements StepHandler {

    private final ImageProvider images;
    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;
    private final AiCallLedger ledger;
    private final StyleReferences styles;
    private final ModelSelector models;

    @Override
    public JobStep step() {
        return JobStep.ILLUSTRATE_PAGE;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        int generation = job.getGeneration();
        PageContext ctx = persistence.pageContext(job.getStorybookId(), job.getPageIndex(), generation);
        if (ctx.status() != StorybookStatus.ILLUSTRATING || ctx.pageGeneration() != generation) {
            return StepOutcome.success();
        }
        if (ctx.imageRowExists()) {
            persistence.ensureQaEnqueued(ctx.bookId(), ctx.pageIndex(), generation);
            return StepOutcome.success();
        }

        String model = models.modelFor(generation - ctx.roundStartGeneration() + 1);
        String key = StorybookKeys.pageImage(ctx.bookId(), ctx.pageIndex(), generation);
        BigDecimal cost = BigDecimal.ZERO;
        if (!store.exists(key)) {
            boolean hasCompanion = ctx.companionSheetKey() != null;
            String prompt = ctx.kind() == PageKind.COVER
                    ? CharacterPrompts.cover(ctx.sceneEn(), hasCompanion, ctx.hijab())
                    : CharacterPrompts.scene(ctx.sceneEn(), ctx.textZone(), hasCompanion
                        && ctx.cast().stream().anyMatch(c -> "COMPANION".equals(c.ref())), ctx.hijab());
            var references = ReferenceAssembler.forPage(store.get(ctx.childSheetKey()), styles.get(ctx.style()),
                    hasCompanion ? store.get(ctx.companionSheetKey()) : null, ctx.cast());
            ImageResult result = images.generate(new ImageRequest(model, prompt, references));
            cost = ledger.recordImage(ctx.bookId(), job.getId(), "IMAGE_PAGE", result);
            store.put(key, result.bytes(), result.mimeType());
        }
        persistence.savePageImage(ctx.bookId(), ctx.pageId(), ctx.pageIndex(), generation, key, model, cost);
        return StepOutcome.success();
    }
}
```

`CharacterPrompts.cover` includes "This is the book cover", which the cover test checks.

- [ ] **Step 5: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=PageIllustrationHandlerTest`
Expected: 7 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/illustration src/test/java/com/doova/ktab/features/storybook/illustration/PageIllustrationHandlerTest.java
git commit -m "feat(storybook): illustrate pages idempotently with reference images"
```

---

### Task 7: Page QA handler and book advancement

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/illustration/QaContext.java`, `PageQaHandler.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/illustration/IllustrationPersistence.java` (QA methods)
- Test: `src/test/java/com/doova/ktab/features/storybook/illustration/PageQaHandlerTest.java`, `IllustrationPersistenceIT.java`

**Interfaces:**
- Consumes: `VisualQa`, `VisualQaResponse` (01); `StorybookRepository.findByIdForUpdate` (02); Tasks 1–6.
- Produces:
  - `record QaContext(Long bookId, StorybookStatus status, Long pageId, Long imageId, PageImageStatus imageStatus, String imageKey, String sceneEn, List<CharacterInScene> cast, String childSheetKey, String companionSheetKey, ArtStyle style)`.
  - `IllustrationPersistence.qaContext(Long bookId, int pageIndex, int generation)` (`readOnly`; `imageId` null when the row is missing).
  - `IllustrationPersistence.recordQa(Long bookId, Long pageId, Long imageId, int generation, VisualQaResponse verdict)` — `@Transactional`; locks the book first (`findByIdForUpdate`), then:
    - passed → image `QA_PASSED`, page `currentImage = image`;
    - failed with `generation − roundStart + 1 < maxGenerations` → image `QA_FAILED`, page `generation = g + 1`, enqueue `ILLUSTRATE_PAGE(pageIndex, g + 1)`;
    - failed on the last attempt → image `FLAGGED`, page `currentImage = image`;
    - then `advance(book)`: only when `ILLUSTRATING`; if every page's image for its current generation is `QA_PASSED` or `ACCEPTED_BY_ADMIN` → `RENDERING` + enqueue `RENDER_PDF(-1, book.pageRegenerations)`; else if none is still pending (`GENERATED`, `QA_FAILED` or missing) and at least one is `FLAGGED` → `QA`.
  - `IllustrationPersistence.advanceIfDone(Long bookId)` — public `@Transactional` wrapper around `advance`, used by admin actions in sub-plan 07.
  - `PageQaHandler` (`QA_PAGE`): no image row, image already judged, or book not `ILLUSTRATING` → `success()`. Else `VisualQa.check(bytes, refs, scene)` → `recordLlm(... VISUAL_QA ...)` → `recordQa(...)` → `success()`.

- [ ] **Step 1: Write the failing tests**

`PageQaHandlerTest.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PageQaHandlerTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final IllustrationPersistence persistence = mock(IllustrationPersistence.class);
    private final PageQaHandler handler = new PageQaHandler(
            new VisualQa(llm, new PromptLibrary(), new StorybookProperties()), store, persistence,
            mock(AiCallLedger.class), new StyleReferences());

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static StorybookJob job() {
        StorybookJob j = new StorybookJob();
        j.setId(1L);
        j.setStorybookId(9L);
        j.setStep(JobStep.QA_PAGE);
        j.setPageIndex(2);
        j.setGeneration(1);
        return j;
    }

    private static QaContext ctx(PageImageStatus status, StorybookStatus book) {
        return new QaContext(9L, book, 100L, 500L, status, "img", "The CHILD reads.",
                List.of(new CharacterInScene("CHILD", "calm")), "child-sheet", null, ArtStyle.SOFT_WATERCOLOR);
    }

    @Test
    void checksAndRecordsTheVerdict() throws Exception {
        when(persistence.qaContext(9L, 2, 1)).thenReturn(ctx(PageImageStatus.GENERATED, StorybookStatus.ILLUSTRATING));
        when(store.get("img")).thenReturn(png());
        when(store.get("child-sheet")).thenReturn(png());
        VisualQaResponse verdict = new VisualQaResponse(true, false, true, true, List.of());
        llm.enqueue(verdict);

        assertThat(handler.handle(job()).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).recordQa(9L, 100L, 500L, 1, verdict);
    }

    @Test
    void alreadyJudgedImagesAreSkipped() {
        when(persistence.qaContext(9L, 2, 1)).thenReturn(ctx(PageImageStatus.QA_PASSED, StorybookStatus.ILLUSTRATING));
        handler.handle(job());
        assertThat(llm.requests()).isEmpty();
        verify(persistence, never()).recordQa(any(), any(), any(), anyInt(), any());
    }

    @Test
    void staleBookIsSkipped() {
        when(persistence.qaContext(9L, 2, 1)).thenReturn(ctx(PageImageStatus.GENERATED, StorybookStatus.FAILED));
        handler.handle(job());
        assertThat(llm.requests()).isEmpty();
    }
}
```

`IllustrationPersistenceIT.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import({IllustrationPersistence.class, JobEnqueuer.class, StorybookStateMachine.class, StorybookProperties.class,
        StoryPersistence.class})
class IllustrationPersistenceIT extends StorybookJpaIT {

    @Autowired IllustrationPersistence persistence;
    @Autowired StoryPersistence stories;
    @Autowired StorybookRepository books;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookPageImageRepository images;
    @Autowired StorybookJobRepository jobs;

    private static final VisualQaResponse PASS = new VisualQaResponse(true, false, true, true, List.of());
    private static final VisualQaResponse FAIL = new VisualQaResponse(false, false, true, true, List.of("hair colour"));

    /** 10-page book in ILLUSTRATING with a GENERATED generation-1 image on the cover and every page. */
    private Storybook illustratedBook() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "qa-" + System.nanoTime() + "@example.com"));
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        for (StorybookPage p : pages.findByStorybook_IdOrderByPageIndexAsc(book.getId())) {
            p.setGeneration(1);
            p.setRoundStartGeneration(1);
            persistence.savePageImage(book.getId(), p.getId(), p.getPageIndex(), 1, "k" + p.getPageIndex(), "m", BigDecimal.ZERO);
        }
        em.flush();
        return book;
    }

    private Long imageId(Long bookId, int pageIndex, int generation) {
        StorybookPage page = pages.findByStorybook_IdAndPageIndex(bookId, pageIndex).orElseThrow();
        return images.findByPage_IdAndGeneration(page.getId(), generation).orElseThrow().getId();
    }

    private void judge(Storybook book, int pageIndex, int generation, VisualQaResponse verdict) {
        StorybookPage page = pages.findByStorybook_IdAndPageIndex(book.getId(), pageIndex).orElseThrow();
        persistence.recordQa(book.getId(), page.getId(), imageId(book.getId(), pageIndex, generation), generation, verdict);
        em.flush();
    }

    @Test
    void lastPassingPageAdvancesTheBookOnce() {
        Storybook book = illustratedBook();
        for (int i = 0; i <= 10; i++) {
            judge(book, i, 1, PASS);
            assertThat(books.findById(book.getId()).orElseThrow().getStatus())
                    .isEqualTo(i < 10 ? StorybookStatus.ILLUSTRATING : StorybookStatus.RENDERING);
        }
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .filteredOn(j -> j.getStep() == JobStep.RENDER_PDF).hasSize(1);
    }

    @Test
    void aFailedPageGetsTheNextGeneration() {
        Storybook book = illustratedBook();
        judge(book, 4, 1, FAIL);

        StorybookPage page = pages.findByStorybook_IdAndPageIndex(book.getId(), 4).orElseThrow();
        assertThat(page.getGeneration()).isEqualTo(2);
        assertThat(images.findById(imageId(book.getId(), 4, 1)).orElseThrow().getStatus()).isEqualTo(PageImageStatus.QA_FAILED);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getPageIndex() + ":" + j.getGeneration())
                .contains(JobStep.ILLUSTRATE_PAGE + ":4:2");
    }

    @Test
    void theFourthFailureFlagsThePageAndParksTheBookInQa() {
        Storybook book = illustratedBook();
        for (int i = 0; i <= 10; i++) {
            if (i != 4) judge(book, i, 1, PASS);
        }
        StorybookPage page4 = pages.findByStorybook_IdAndPageIndex(book.getId(), 4).orElseThrow();
        for (int g = 1; g <= 4; g++) {
            if (g > 1) {
                persistence.savePageImage(book.getId(), page4.getId(), 4, g, "k4-" + g, "m", BigDecimal.ZERO);
            }
            judge(book, 4, g, FAIL);
        }

        assertThat(images.findById(imageId(book.getId(), 4, 4)).orElseThrow().getStatus()).isEqualTo(PageImageStatus.FLAGGED);
        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.QA);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='PageQaHandlerTest,IllustrationPersistenceIT'`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `QaContext` and the QA persistence**

`QaContext.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.story.CharacterInScene;

import java.util.List;

public record QaContext(Long bookId, StorybookStatus status, Long pageId, Long imageId, PageImageStatus imageStatus,
                        String imageKey, String sceneEn, List<CharacterInScene> cast, String childSheetKey,
                        String companionSheetKey, ArtStyle style) {
}
```

Add to `IllustrationPersistence` (with imports for `StorybookPage`, `StorybookPageImage`, `PageImageStatus`, `VisualQaResponse`, `java.util.EnumSet`, `java.util.List`, `java.util.Set`):
```java
    private static final Set<PageImageStatus> DONE_OK = EnumSet.of(PageImageStatus.QA_PASSED, PageImageStatus.ACCEPTED_BY_ADMIN);

    @Transactional(readOnly = true)
    public QaContext qaContext(Long bookId, int pageIndex, int generation) {
        Storybook book = books.findById(bookId).orElseThrow();
        StorybookPage page = pages.findByStorybook_IdAndPageIndex(bookId, pageIndex).orElseThrow();
        var image = images.findByPage_IdAndGeneration(page.getId(), generation);
        return new QaContext(bookId, book.getStatus(), page.getId(),
                image.map(StorybookPageImage::getId).orElse(null),
                image.map(StorybookPageImage::getStatus).orElse(null),
                image.map(StorybookPageImage::getImageKey).orElse(null),
                page.getSceneEn(), page.getCharacters(),
                characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).map(StorybookCharacter::getSheetKey).orElse(null),
                characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION).map(StorybookCharacter::getSheetKey).orElse(null),
                book.getStyle());
    }

    @Transactional
    public void recordQa(Long bookId, Long pageId, Long imageId, int generation, VisualQaResponse verdict) {
        Storybook book = books.findByIdForUpdate(bookId).orElseThrow(); // serializes advancement per book
        StorybookPage page = pages.findById(pageId).orElseThrow();
        StorybookPageImage image = images.findById(imageId).orElseThrow();
        image.setQaResult(verdict);
        int attemptInRound = generation - page.getRoundStartGeneration() + 1;
        if (verdict.passed()) {
            image.setStatus(PageImageStatus.QA_PASSED);
            page.setCurrentImage(image);
        } else if (attemptInRound < properties.getImage().getMaxGenerations()) {
            image.setStatus(PageImageStatus.QA_FAILED);
            page.setGeneration(generation + 1);
            enqueuer.enqueue(bookId, JobStep.ILLUSTRATE_PAGE, page.getPageIndex(), generation + 1);
        } else {
            image.setStatus(PageImageStatus.FLAGGED);
            page.setCurrentImage(image);
        }
        advance(book);
    }

    @Transactional
    public void advanceIfDone(Long bookId) {
        advance(books.findByIdForUpdate(bookId).orElseThrow());
    }

    private void advance(Storybook book) {
        if (book.getStatus() != StorybookStatus.ILLUSTRATING) {
            return;
        }
        boolean allOk = true;
        boolean anyFlagged = false;
        for (StorybookPage p : pages.findByStorybook_IdOrderByPageIndexAsc(book.getId())) {
            PageImageStatus status = images.findByPage_IdAndGeneration(p.getId(), p.getGeneration())
                    .map(StorybookPageImage::getStatus).orElse(null);
            if (status == null || status == PageImageStatus.GENERATED || status == PageImageStatus.QA_FAILED) {
                return; // still in progress
            }
            if (!DONE_OK.contains(status)) {
                allOk = false;
                anyFlagged |= status == PageImageStatus.FLAGGED;
            }
        }
        if (allOk) {
            stateMachine.transition(book, StorybookStatus.RENDERING);
            enqueuer.enqueue(book.getId(), JobStep.RENDER_PDF, -1, book.getPageRegenerations());
        } else if (anyFlagged) {
            stateMachine.transition(book, StorybookStatus.QA);
        }
    }
```

- [ ] **Step 4: Implement the handler**

```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PageQaHandler implements StepHandler {

    private final VisualQa visualQa;
    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;
    private final AiCallLedger ledger;
    private final StyleReferences styles;

    @Override
    public JobStep step() {
        return JobStep.QA_PAGE;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        QaContext ctx = persistence.qaContext(job.getStorybookId(), job.getPageIndex(), job.getGeneration());
        if (ctx.imageId() == null || ctx.imageStatus() != PageImageStatus.GENERATED
                || ctx.status() != StorybookStatus.ILLUSTRATING) {
            return StepOutcome.success();
        }
        var references = ReferenceAssembler.forPage(store.get(ctx.childSheetKey()), styles.get(ctx.style()),
                ctx.companionSheetKey() == null ? null : store.get(ctx.companionSheetKey()), ctx.cast());
        LlmCall<VisualQaResponse> check = visualQa.check(store.get(ctx.imageKey()), references, ctx.sceneEn());
        ledger.recordLlm(ctx.bookId(), job.getId(), LlmPurpose.VISUAL_QA, check);
        persistence.recordQa(ctx.bookId(), ctx.pageId(), ctx.imageId(), job.getGeneration(), check.value());
        return StepOutcome.success();
    }
}
```

`StyleReferences.get` returns the 1×1 test PNG in unit tests; `VisualQa` downsizes all images, which works on any readable PNG.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='PageQaHandlerTest,IllustrationPersistenceIT'`
Expected: 6 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/illustration src/test/java/com/doova/ktab/features/storybook/illustration
git commit -m "feat(storybook): add visual QA step with retries, flagging and book advancement"
```

---

### Task 8: Final-review page regeneration

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/illustration/PageRegenerationService.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/illustration/PageRegenerationServiceIT.java`

**Interfaces:**
- Consumes: `StorybookAccessGuard`, repositories (02); `JobEnqueuer`, `StorybookStateMachine` (03); `StorybookProperties.getLimits().getPageRegenerationsPerBook()` (01).
- Produces:
  - `PageRegenerationService.regenerate(User owner, Long bookId, int pageIndex)` — `@Transactional`; book `READY` (else 409); `pageRegenerations < limit` (else 400 `STORYBOOK_LIMIT_REACHED`); page exists (else 404 `STORYBOOK_NOT_FOUND`); `pageRegenerations++`; `READY → ILLUSTRATING` (D9); `g = generation + 1`, `generation = g`, `roundStartGeneration = g`; enqueue `ILLUSTRATE_PAGE(pageIndex, g)`. When the page passes QA, `advance` enqueues `RENDER_PDF(-1, pageRegenerations)` — a new key, so the book is rendered again.
  - Endpoint: `POST /storybook/books/{bookId}/pages/{pageIndex}/regenerate` → 202.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({PageRegenerationService.class, StorybookAccessGuard.class, JobEnqueuer.class, StorybookStateMachine.class,
        StorybookProperties.class, StoryPersistence.class})
class PageRegenerationServiceIT extends StorybookJpaIT {

    @Autowired PageRegenerationService regeneration;
    @Autowired StoryPersistence stories;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookJobRepository jobs;

    @Test
    void regeneratesOnePageAndCountsIt() {
        User owner = UserFixtures.reader(em, "regen@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);
        pages.findByStorybook_IdAndPageIndex(book.getId(), 6).orElseThrow().setGeneration(2);
        book.setStatus(StorybookStatus.READY);
        em.flush();

        regeneration.regenerate(owner, book.getId(), 6);

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(book.getPageRegenerations()).isEqualTo(1);
        var page = pages.findByStorybook_IdAndPageIndex(book.getId(), 6).orElseThrow();
        assertThat(page.getGeneration()).isEqualTo(3);
        assertThat(page.getRoundStartGeneration()).isEqualTo(3);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getPageIndex() + ":" + j.getGeneration())
                .contains(JobStep.ILLUSTRATE_PAGE + ":6:3");
    }

    @Test
    void limitsAndStateAreEnforced() {
        User owner = UserFixtures.reader(em, "regen2@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);

        assertThatThrownBy(() -> regeneration.regenerate(owner, book.getId(), 1)).isInstanceOf(StorybookStateConflictException.class);

        book.setStatus(StorybookStatus.READY);
        assertThatThrownBy(() -> regeneration.regenerate(owner, book.getId(), 42)).isInstanceOf(ResourceNotFoundException.class);

        book.setPageRegenerations(3);
        assertThatThrownBy(() -> regeneration.regenerate(owner, book.getId(), 1))
                .isInstanceOf(BadRequestException.class).hasMessage("STORYBOOK_LIMIT_REACHED");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=PageRegenerationServiceIT`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PageRegenerationService {

    private final StorybookAccessGuard guard;
    private final StorybookPageRepository pages;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final StorybookProperties properties;

    /** Spec "Final review": the parent can regenerate individual pages a limited number of times (D9). */
    @Transactional
    public void regenerate(User owner, Long bookId, int pageIndex) {
        Storybook book = guard.requireOwned(bookId, owner);
        if (book.getStatus() != StorybookStatus.READY) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        StorybookPage page = pages.findByStorybook_IdAndPageIndex(bookId, pageIndex)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.STORYBOOK_NOT_FOUND));
        if (book.getPageRegenerations() >= properties.getLimits().getPageRegenerationsPerBook()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_LIMIT_REACHED);
        }
        book.setPageRegenerations(book.getPageRegenerations() + 1);
        stateMachine.transition(book, StorybookStatus.ILLUSTRATING);
        int next = page.getGeneration() + 1;
        page.setGeneration(next);
        page.setRoundStartGeneration(next);
        enqueuer.enqueue(bookId, JobStep.ILLUSTRATE_PAGE, pageIndex, next);
    }
}
```

The page lookup comes before the limit check so a bad page index is a 404 regardless of the counter; the test order above relies on that.

- [ ] **Step 4: Add the endpoint**

In `StorybookController`, add the field `private final com.doova.ktab.features.storybook.illustration.PageRegenerationService pageRegenerationService;` (after `lookService`) and:
```java
    @PostMapping("/books/{bookId}/pages/{pageIndex}/regenerate")
    public ResponseEntity<ApiResponse<Void>> regeneratePage(@CurrentUser User user, @PathVariable Long bookId,
                                                            @PathVariable int pageIndex) {
        pageRegenerationService.regenerate(user, bookId, pageIndex);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }
```
Add `mock(PageRegenerationService.class)` as the next argument in `StorybookControllerTest.setUp()`.

- [ ] **Step 5: Run the tests and the storybook suite**

Run: `./mvnw -q test -Dtest='PageRegenerationServiceIT,StorybookControllerTest' && ./mvnw -q test -Dtest='com.doova.ktab.features.storybook.**'`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): allow limited final-review page regeneration"
```
