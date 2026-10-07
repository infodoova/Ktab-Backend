package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.illustration.IllustrationPersistence;
import com.doova.ktab.features.storybook.illustration.PhotoPurgeHandler;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import com.doova.ktab.features.storybook.web.StorybookService;
import com.doova.ktab.features.storybook.web.dto.CharacterInput;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "ktab.storybook.enabled=true",
        "ktab.storybook.credits.required=false",
        "ktab.storybook.worker.poll-delay=999999s"
})
@DisplayName("Live Integration Test: Real Image Upload, S3/R2 Encryption & Purge Verification")
class LivePhotoIntakeStorageIT {

    @Autowired
    private StorybookService storybookService;

    @Autowired
    private StorybookRepository storybookRepository;

    @Autowired
    private StorybookCharacterRepository characterRepository;

    @Autowired
    private StorybookAssetStore assetStore;

    @Autowired
    private PhotoVault photoVault;

    @Autowired
    private IllustrationPersistence persistence;

    @Autowired
    private PhotoPurgeHandler photoPurgeHandler;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private UserRepository userRepository;

    @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}")
    private String bucketName;

    @Test
    @DisplayName("Upload real images in a single request, inspect encrypted objects directly in S3, decrypt, and purge")
    void testLiveUploadToS3AndVerification() throws Exception {
        System.out.println("\n=======================================================");
        System.out.println("  LIVE S3 / R2 PHOTO STORAGE & ENCRYPTION VERIFICATION ");
        System.out.println("=======================================================");
        System.out.println("Target Storage Bucket: " + bucketName);

        // 1. Prepare Real Image from Disk
        Path imgPath = Path.of("src/main/resources/storybook/styles/soft_watercolor.png");
        byte[] realImageBytes = Files.readAllBytes(imgPath);
        assertThat(realImageBytes.length).isGreaterThan(1000);
        System.out.printf("Loaded Real Image: %s (%d bytes)%n", imgPath.getFileName(), realImageBytes.length);

        MockMultipartFile childPhotoFile = new MockMultipartFile(
                "childPhoto", "real_child_photo.png", "image/png", realImageBytes
        );
        MockMultipartFile companionPhotoFile = new MockMultipartFile(
                "companionPhoto", "pet_cat.png", "image/png", realImageBytes
        );
        MockMultipartFile grandpaPhotoFile = new MockMultipartFile(
                "characterPhotos", "grandpa.png", "image/png", realImageBytes
        );

        // 2. Setup Test User
        String email = "live-photo-test-" + System.currentTimeMillis() + "@ktab.app";
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Live");
        user.setLastName("Tester");
        user.setPasswordDigest("$2a$10$notrealpasswordhashfortestingonly");
        user.setRole("20");
        user = userRepository.save(user);

        // 3. Build Single Storybook Creation Request with child, companion & grandpa
        CreateChildProfileRequest child = new CreateChildProfileRequest(
                "سامي",
                com.doova.ktab.features.storybook.enums.ChildGender.BOY,
                com.doova.ktab.features.storybook.enums.AgeBand.AGE_6_8,
                com.doova.ktab.features.storybook.support.StoryFixtures.APPEARANCE
        );
        CompanionSpec companion = new CompanionSpec(
                CompanionSpec.CompanionType.CAT,
                "مشمش",
                null,
                CompanionSpec.PetColor.ORANGE
        );
        CharacterInput grandpa = new CharacterInput(
                "grandpa", "الجد سالم", "human", "mentor", "grandfather", "thobe", null, null
        );

        CreateStorybookRequest request = new CreateStorybookRequest(
                null, child, null, companion, null, null, null,
                ArtStyle.SOFT_WATERCOLOR,
                15,
                LanguageVariety.MSA,
                TashkeelLevel.PARTIAL,
                "إلى طفلي الحبيب",
                "المغامرة في الحديقة",
                null, null, null, null, null,
                List.of(grandpa),
                null, null, true
        );

        // 4. Execute Single-Step Creation with Multipart Photos
        System.out.println("\n--- Step 1: Creating Storybook with 3 Photos in Single Request ---");
        StorybookDetail detail = storybookService.create(
                user, request, childPhotoFile, companionPhotoFile, List.of(grandpaPhotoFile), true
        );
        Long bookId = detail.id();
        System.out.println("Created Storybook ID: " + bookId);

        String childKey = StorybookKeys.photo(bookId);
        String companionKey = StorybookKeys.characterPhoto(bookId, "companion");
        String grandpaKey = StorybookKeys.characterPhoto(bookId, "grandpa");

        System.out.printf("Expected S3 Key (Child):     %s%n", childKey);
        System.out.printf("Expected S3 Key (Companion): %s%n", companionKey);
        System.out.printf("Expected S3 Key (Grandpa):   %s%n", grandpaKey);

        // 5. Inspect Objects in Real Storage via AssetStore & S3Client
        System.out.println("\n--- Step 2: Direct Storage Verification ---");
        assertThat(assetStore.exists(childKey)).as("Child photo must exist in store").isTrue();
        assertThat(assetStore.exists(companionKey)).as("Companion photo must exist in store").isTrue();
        assertThat(assetStore.exists(grandpaKey)).as("Grandpa photo must exist in store").isTrue();

        byte[] s3ChildBytes = assetStore.get(childKey);
        System.out.printf("Retrieved Child Photo from Storage: %d bytes%n", s3ChildBytes.length);

        // Verify that raw bytes stored in S3 are NOT valid image format
        BufferedImage attemptImageRead = ImageIO.read(new ByteArrayInputStream(s3ChildBytes));
        assertThat(attemptImageRead).as("Stored blob must NOT be plain image").isNull();
        System.out.println("Storage Privacy Verified: Object in S3 is encrypted binary data (not readable as image).");

        // 6. Verify In-Memory Decryption
        System.out.println("\n--- Step 3: Vault Decryption Verification ---");
        byte[] decryptedPhoto = photoVault.decrypt(s3ChildBytes);
        assertThat(decryptedPhoto).isNotNull();
        assertThat(decryptedPhoto.length).isGreaterThan(1000);

        // Decrypted content is valid JPEG (starts with 0xFF 0xD8)
        assertThat(decryptedPhoto[0]).isEqualTo((byte) 0xFF);
        assertThat(decryptedPhoto[1]).isEqualTo((byte) 0xD8);

        BufferedImage validImg = ImageIO.read(new ByteArrayInputStream(decryptedPhoto));
        assertThat(validImg).as("Decrypted photo must be a valid image").isNotNull();
        System.out.printf("Decryption Successful: Image Dimensions = %dx%d (Clean Sanitized JPEG)%n",
                validImg.getWidth(), validImg.getHeight());

        // 7. Test Purge Handler (Deletion from S3)
        System.out.println("\n--- Step 4: Photo Purge Verification ---");
        StorybookJob job = new StorybookJob();
        job.setStorybookId(bookId);
        photoPurgeHandler.handle(job);

        assertThat(assetStore.exists(childKey)).as("Child photo must be purged from S3").isFalse();
        assertThat(assetStore.exists(companionKey)).as("Companion photo must be purged from S3").isFalse();
        assertThat(assetStore.exists(grandpaKey)).as("Grandpa photo must be purged from S3").isFalse();
        System.out.println("Purge Complete: All photos successfully deleted from S3 storage!");
        System.out.println("=======================================================\n");
    }
}
