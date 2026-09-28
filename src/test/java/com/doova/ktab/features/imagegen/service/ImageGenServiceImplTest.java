package com.doova.ktab.features.imagegen.service;

import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.imagegen.config.ImageGenProperties;
import com.doova.ktab.features.imagegen.dto.request.GenerateImageRequest;
import com.doova.ktab.features.imagegen.dto.response.GenerateImageResponse;
import com.doova.ktab.features.imagegen.dto.response.ImageStatusResponse;
import com.doova.ktab.features.imagegen.enums.ImageAspectRatio;
import com.doova.ktab.features.imagegen.enums.ImageGenerationStatus;
import com.doova.ktab.features.imagegen.enums.ImageTheme;
import com.doova.ktab.features.imagegen.event.model.ImageGenerationRequestedEvent;
import com.doova.ktab.features.imagegen.exception.ImageDuplicateInFlightException;
import com.doova.ktab.features.imagegen.exception.ImageForbiddenException;
import com.doova.ktab.features.imagegen.exception.ImageNotFoundException;
import com.doova.ktab.features.imagegen.exception.ImageQuotaExceededException;
import com.doova.ktab.features.imagegen.model.GeneratedImage;
import com.doova.ktab.features.imagegen.prompt.ImagePromptBuilder;
import com.doova.ktab.features.imagegen.repository.GeneratedImageRepository;
import com.doova.ktab.features.imagegen.service.impl.ImageGenServiceImpl;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.imagegen.prompt.BookPromptContext;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageGenServiceImplTest {

    @Mock
    private GeneratedImageRepository imageRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ImagePromptBuilder promptBuilder;

    @Mock
    private CloudflareImageStorageService storageService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private org.springframework.context.MessageSource messageSource;

    private ImageGenProperties properties;
    private ImageGenServiceImpl imageGenService;

    private User testUser;
    private Book testBook;

    @BeforeEach
    void setUp() {
        properties = new ImageGenProperties();
        properties.setRateLimitPerHour(10);
        properties.setMaxConcurrentPerUser(3);
        properties.setAiModel("gemini-2.5-flash-image");

        imageGenService = new ImageGenServiceImpl(
                imageRepository,
                bookRepository,
                promptBuilder,
                storageService,
                attachmentService,
                fileStorageService,
                properties,
                eventPublisher,
                messageSource
        );

        testUser = new User();
        testUser.setId(100L);
        testUser.setEmail("reader@ktab.ai");

        testBook = new Book();
        testBook.setId(42L);
        testBook.setTitle("The Desert Citadel");
        testBook.setCustomAuthorName("Tariq Al-Mansoor");
        testBook.setDescription("An epic fortress in the desert dunes.");
    }

    @Test
    @DisplayName("submitGeneration should persist QUEUED entity and dispatch event when valid")
    void submitGeneration_validRequest_persistsQueuedRecordAndDispatchesEvent() {
        GenerateImageRequest request = new GenerateImageRequest(
                "A grand citadel in the dunes",
                ImageTheme.WATERCOLOR,
                ImageAspectRatio.PORTRAIT_3_4,
                "Sunset glow"
        );

        Attachment coverMock = Attachment.builder()
                .fileName("cover.jpg")
                .storagePath("books/42/cover.jpg")
                .entityId(42L)
                .build();

        when(bookRepository.findById(42L)).thenReturn(Optional.of(testBook));
        when(imageRepository.countByUserIdAndCreatedAtAfterAndStatusNot(eq(100L), any(LocalDateTime.class), eq(ImageGenerationStatus.FAILED))).thenReturn(2L);
        when(imageRepository.countByUserIdAndStatusIn(eq(100L), anyCollection())).thenReturn(0L);
        when(attachmentService.getAttachment(eq(42L), eq(BookResponseBuilderService.BOOK_ENTITY_TYPE), eq(BookResponseBuilderService.COVER_IMAGE_TYPE)))
                .thenReturn(Optional.of(coverMock));
        when(fileStorageService.getFileUrl(eq("books/42/cover.jpg"), any(UrlStrategy.class)))
                .thenReturn("https://storage.ktab.ai/books/42/cover.jpg");

        ArgumentCaptor<BookPromptContext> bookContextCaptor = ArgumentCaptor.forClass(BookPromptContext.class);
        when(promptBuilder.buildPrompt(any(), any(), any(), any(), bookContextCaptor.capture())).thenReturn("FULL PROMPT");
        when(promptBuilder.computePromptHash(any(), any(), any())).thenReturn("hash-12345");
        when(promptBuilder.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(imageRepository.existsInFlightDuplicate(eq(100L), eq(42L), eq("hash-12345"), anyCollection())).thenReturn(false);

        GeneratedImage savedMock = new GeneratedImage();
        UUID generatedId = UUID.randomUUID();
        savedMock.setId(generatedId);
        savedMock.setBook(testBook);
        savedMock.setUser(testUser);
        savedMock.setStatus(ImageGenerationStatus.QUEUED);
        when(imageRepository.save(any(GeneratedImage.class))).thenReturn(savedMock);

        ImageStatusResponse response = imageGenService.submitGeneration(42L, request, testUser);

        assertNotNull(response);
        assertEquals(generatedId, response.imageId());
        assertEquals(42L, response.bookId());
        assertEquals(ImageGenerationStatus.QUEUED, response.status());

        // Verify book entity context passed to prompt builder
        BookPromptContext capturedContext = bookContextCaptor.getValue();
        assertNotNull(capturedContext);
        assertEquals("The Desert Citadel", capturedContext.title());
        assertEquals("Tariq Al-Mansoor", capturedContext.author());
        assertEquals("An epic fortress in the desert dunes.", capturedContext.description());
        assertEquals("https://storage.ktab.ai/books/42/cover.jpg", capturedContext.coverImageUrl());

        ArgumentCaptor<ImageGenerationRequestedEvent> eventCaptor = ArgumentCaptor.forClass(ImageGenerationRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ImageGenerationRequestedEvent capturedEvent = eventCaptor.getValue();
        assertEquals(generatedId, capturedEvent.imageId());
        assertEquals(42L, capturedEvent.bookId());
        assertEquals(100L, capturedEvent.userId());
        assertEquals("FULL PROMPT", capturedEvent.prompt());
    }

    @Test
    @DisplayName("submitGeneration should throw ResourceNotFoundException when book does not exist")
    void submitGeneration_bookNotFound_throwsResourceNotFoundException() {
        GenerateImageRequest request = new GenerateImageRequest(
                "Context",
                ImageTheme.CARTOON,
                ImageAspectRatio.SQUARE_1_1,
                null
        );

        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                imageGenService.submitGeneration(999L, request, testUser));
    }

    @Test
    @DisplayName("submitGeneration should throw ImageQuotaExceededException when hourly quota exceeded")
    void submitGeneration_rateLimitExceeded_throwsImageQuotaExceededException() {
        GenerateImageRequest request = new GenerateImageRequest(
                "Context",
                ImageTheme.CARTOON,
                ImageAspectRatio.SQUARE_1_1,
                null
        );

        when(bookRepository.findById(42L)).thenReturn(Optional.of(testBook));
        when(imageRepository.countByUserIdAndCreatedAtAfterAndStatusNot(eq(100L), any(LocalDateTime.class), eq(ImageGenerationStatus.FAILED))).thenReturn(100L);

        assertThrows(ImageQuotaExceededException.class, () ->
                imageGenService.submitGeneration(42L, request, testUser));
    }

    @Test
    @DisplayName("submitGeneration should throw ImageQuotaExceededException when concurrent requests limit reached")
    void submitGeneration_concurrentLimitExceeded_throwsImageQuotaExceededException() {
        GenerateImageRequest request = new GenerateImageRequest(
                "Context",
                ImageTheme.CARTOON,
                ImageAspectRatio.SQUARE_1_1,
                null
        );

        when(bookRepository.findById(42L)).thenReturn(Optional.of(testBook));
        when(imageRepository.countByUserIdAndCreatedAtAfterAndStatusNot(eq(100L), any(LocalDateTime.class), eq(ImageGenerationStatus.FAILED))).thenReturn(2L);
        when(imageRepository.countByUserIdAndStatusIn(eq(100L), anyCollection())).thenReturn(10L);

        assertThrows(ImageQuotaExceededException.class, () ->
                imageGenService.submitGeneration(42L, request, testUser));
    }

    @Test
    @DisplayName("submitGeneration should throw ImageDuplicateInFlightException when same prompt is already in flight")
    void submitGeneration_duplicateInFlight_throwsImageDuplicateInFlightException() {
        GenerateImageRequest request = new GenerateImageRequest(
                "Context",
                ImageTheme.CARTOON,
                ImageAspectRatio.SQUARE_1_1,
                null
        );

        when(bookRepository.findById(42L)).thenReturn(Optional.of(testBook));
        when(imageRepository.countByUserIdAndCreatedAtAfterAndStatusNot(eq(100L), any(LocalDateTime.class), eq(ImageGenerationStatus.FAILED))).thenReturn(0L);
        when(imageRepository.countByUserIdAndStatusIn(eq(100L), anyCollection())).thenReturn(0L);
        when(promptBuilder.computePromptHash(any(), any(), any())).thenReturn("existing-hash");
        when(imageRepository.existsInFlightDuplicate(eq(100L), eq(42L), eq("existing-hash"), anyCollection())).thenReturn(true);

        assertThrows(ImageDuplicateInFlightException.class, () ->
                imageGenService.submitGeneration(42L, request, testUser));
    }

    @Test
    @DisplayName("getStatus should return status with resolved Cloudflare URL for completed image")
    void getStatus_validOwner_returnsStatusWithResolvedUrl() {
        UUID imageId = UUID.randomUUID();
        GeneratedImage image = new GeneratedImage();
        image.setId(imageId);
        image.setBook(testBook);
        image.setUser(testUser);
        image.setStatus(ImageGenerationStatus.COMPLETED);
        image.setStorageKey("books/42/generated-images/100/sample.png");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));
        when(storageService.resolveImageUrl("books/42/generated-images/100/sample.png"))
                .thenReturn("https://media.ktab.ai/books/42/generated-images/100/sample.png");

        ImageStatusResponse response = imageGenService.getStatus(42L, imageId, testUser);

        assertNotNull(response);
        assertEquals(ImageGenerationStatus.COMPLETED, response.status());
        assertEquals("https://media.ktab.ai/books/42/generated-images/100/sample.png", response.imageUrl());
    }

    @Test
    @DisplayName("getStatus should throw ImageForbiddenException when requester is not image owner")
    void getStatus_notOwner_throwsImageForbiddenException() {
        UUID imageId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(999L);

        GeneratedImage image = new GeneratedImage();
        image.setId(imageId);
        image.setBook(testBook);
        image.setUser(otherUser);

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

        assertThrows(ImageForbiddenException.class, () ->
                imageGenService.getStatus(42L, imageId, testUser));
    }

    @Test
    @DisplayName("getStatus should throw ImageNotFoundException when image does not exist")
    void getStatus_imageNotFound_throwsImageNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(imageRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ImageNotFoundException.class, () ->
                imageGenService.getStatus(42L, nonExistentId, testUser));
    }

    @Test
    @DisplayName("getImage should return full details with resolved Cloudflare URL for owner")
    void getImage_validOwner_returnsDetailedResponse() {
        UUID imageId = UUID.randomUUID();
        GeneratedImage image = new GeneratedImage();
        image.setId(imageId);
        image.setBook(testBook);
        image.setUser(testUser);
        image.setUserContext("Detailed Scene");
        image.setTheme(ImageTheme.WATERCOLOR);
        image.setAspectRatio("3:4");
        image.setStatus(ImageGenerationStatus.COMPLETED);
        image.setStorageKey("books/42/generated-images/100/detailed.png");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));
        when(storageService.resolveImageUrl("books/42/generated-images/100/detailed.png"))
                .thenReturn("https://media.ktab.ai/books/42/generated-images/100/detailed.png");

        GenerateImageResponse response = imageGenService.getImage(42L, imageId, testUser);

        assertNotNull(response);
        assertEquals(imageId, response.imageId());
        assertEquals("Detailed Scene", response.context());
        assertEquals(ImageTheme.WATERCOLOR, response.theme());
        assertEquals("3:4", response.aspectRatio());
        assertEquals("https://media.ktab.ai/books/42/generated-images/100/detailed.png", response.imageUrl());
    }

    @Test
    @DisplayName("listImages should return paginated list of generated images with resolved URLs")
    void listImages_validBook_returnsPaginatedResponses() {
        when(bookRepository.existsById(42L)).thenReturn(true);

        GeneratedImage img1 = new GeneratedImage();
        img1.setId(UUID.randomUUID());
        img1.setBook(testBook);
        img1.setUser(testUser);
        img1.setUserContext("Scene 1");
        img1.setTheme(ImageTheme.OIL_PAINTING);
        img1.setAspectRatio("1:1");
        img1.setStatus(ImageGenerationStatus.COMPLETED);
        img1.setStorageKey("key1.png");

        Page<GeneratedImage> pagedImages = new PageImpl<>(List.of(img1));
        when(imageRepository.findAllByBookIdAndUserId(eq(42L), eq(100L), any(PageRequest.class)))
                .thenReturn(pagedImages);
        when(storageService.resolveImageUrl("key1.png")).thenReturn("https://media.ktab.ai/key1.png");

        Page<GenerateImageResponse> result = imageGenService.listImages(42L, testUser, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("https://media.ktab.ai/key1.png", result.getContent().get(0).imageUrl());
    }

    @Test
    @DisplayName("deleteImage should delete from Cloudflare R2 and remove repository record")
    void deleteImage_validOwner_deletesFromR2AndDatabase() {
        UUID imageId = UUID.randomUUID();
        GeneratedImage image = new GeneratedImage();
        image.setId(imageId);
        image.setBook(testBook);
        image.setUser(testUser);
        image.setStorageKey("books/42/generated-images/100/test.png");

        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

        imageGenService.deleteImage(42L, imageId, testUser);

        verify(storageService).deleteImage("books/42/generated-images/100/test.png");
        verify(imageRepository).delete(image);
    }

    @Test
    @DisplayName("getAvailableFilters should return all supported themes and aspect ratios with localized fields")
    void getAvailableFilters_invoked_returnsAllThemesAndAspectRatios() {
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    if ("imagegen.theme.watercolor.name".equals(key)) return "ألوان مائية";
                    if ("imagegen.aspect_ratio.portrait_3_4.name".equals(key)) return "عمودي للكتب (3:4)";
                    return inv.getArgument(2);
                });

        var filters = imageGenService.getAvailableFilters();

        assertNotNull(filters);
        assertFalse(filters.themes().isEmpty());
        assertFalse(filters.aspectRatios().isEmpty());
        assertEquals(ImageTheme.values().length, filters.themes().size());
        assertEquals(ImageAspectRatio.values().length, filters.aspectRatios().size());

        var watercolor = filters.themes().stream()
                .filter(t -> "WATERCOLOR".equals(t.value()))
                .findFirst()
                .orElseThrow();
        assertEquals("ألوان مائية", watercolor.displayName());

        var portrait = filters.aspectRatios().stream()
                .filter(r -> "PORTRAIT_3_4".equals(r.value()))
                .findFirst()
                .orElseThrow();
        assertEquals("عمودي للكتب (3:4)", portrait.displayName());
    }

    @Test
    @DisplayName("listAllReaderImages should return paginated cross-book images with book titles")
    void listAllReaderImages_validUser_returnsCrossBookImages() {
        GeneratedImage img = new GeneratedImage();
        img.setId(UUID.randomUUID());
        img.setBook(testBook);
        img.setUser(testUser);
        img.setUserContext("All books scene");
        img.setTheme(ImageTheme.FANTASY_ART);
        img.setAspectRatio("16:9");
        img.setStatus(ImageGenerationStatus.COMPLETED);
        img.setStorageKey("cross1.png");

        Page<GeneratedImage> pagedImages = new PageImpl<>(List.of(img));
        when(imageRepository.findAllByUserIdAndOptionalBookId(eq(100L), isNull(), any(PageRequest.class)))
                .thenReturn(pagedImages);
        when(storageService.resolveImageUrl("cross1.png")).thenReturn("https://media.ktab.ai/cross1.png");

        Page<GenerateImageResponse> result = imageGenService.listAllReaderImages(null, testUser, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(42L, result.getContent().get(0).bookId());
        assertEquals("The Desert Citadel", result.getContent().get(0).bookTitle());
    }

    @Test
    @DisplayName("getGroupedReaderImages should return images grouped/separated by book")
    void getGroupedReaderImages_validUser_returnsImagesGroupedByBook() {
        GeneratedImage img1 = new GeneratedImage();
        img1.setId(UUID.randomUUID());
        img1.setBook(testBook);
        img1.setUser(testUser);
        img1.setUserContext("Scene A");
        img1.setTheme(ImageTheme.REALISTIC);
        img1.setAspectRatio("1:1");
        img1.setStatus(ImageGenerationStatus.COMPLETED);
        img1.setStorageKey("keyA.png");

        when(imageRepository.findAllByUserIdWithBook(100L)).thenReturn(List.of(img1));
        when(storageService.resolveImageUrl("keyA.png")).thenReturn("https://media.ktab.ai/keyA.png");

        var grouped = imageGenService.getGroupedReaderImages(testUser);

        assertNotNull(grouped);
        assertEquals(1, grouped.size());
        assertEquals(42L, grouped.get(0).bookId());
        assertEquals("The Desert Citadel", grouped.get(0).bookTitle());
        assertEquals(1, grouped.get(0).totalImages());
        assertEquals(1, grouped.get(0).images().size());
    }

    @Test
    @DisplayName("getReaderBooksWithImages should return distinct books summary with counts")
    void getReaderBooksWithImages_validUser_returnsBookSummaries() {
        var summary = new com.doova.ktab.features.imagegen.dto.response.ReaderBookImageSummary(42L, "The Desert Citadel", 5L);
        when(imageRepository.findDistinctBooksWithImageCounts(100L)).thenReturn(List.of(summary));

        var result = imageGenService.getReaderBooksWithImages(testUser);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(42L, result.get(0).bookId());
        assertEquals("The Desert Citadel", result.get(0).bookTitle());
        assertEquals(5L, result.get(0).imageCount());
    }
}
