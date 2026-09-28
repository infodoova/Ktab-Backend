package com.doova.ktab.features.imagegen.service.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.imagegen.config.ImageGenProperties;
import com.doova.ktab.features.imagegen.dto.request.GenerateImageRequest;
import com.doova.ktab.features.imagegen.dto.response.GenerateImageResponse;
import com.doova.ktab.features.imagegen.dto.response.ImageStatusResponse;
import com.doova.ktab.features.imagegen.enums.ImageGenerationStatus;
import com.doova.ktab.features.imagegen.event.model.ImageGenerationRequestedEvent;
import com.doova.ktab.features.imagegen.exception.ImageDuplicateInFlightException;
import com.doova.ktab.features.imagegen.exception.ImageForbiddenException;
import com.doova.ktab.features.imagegen.exception.ImageNotFoundException;
import com.doova.ktab.features.imagegen.exception.ImageQuotaExceededException;
import com.doova.ktab.features.imagegen.model.GeneratedImage;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.imagegen.prompt.BookPromptContext;
import com.doova.ktab.features.imagegen.prompt.ImagePromptBuilder;
import com.doova.ktab.features.imagegen.repository.GeneratedImageRepository;
import com.doova.ktab.features.imagegen.service.CloudflareImageStorageService;
import com.doova.ktab.features.imagegen.service.ImageGenService;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenServiceImpl implements ImageGenService {

    private final GeneratedImageRepository imageRepository;
    private final BookRepository bookRepository;
    private final ImagePromptBuilder promptBuilder;
    private final CloudflareImageStorageService storageService;
    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;
    private final ImageGenProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final org.springframework.context.MessageSource messageSource;

    @Override
    @Transactional
    public ImageStatusResponse submitGeneration(Long bookId, GenerateImageRequest request, User user) {
        log.info("Received image generation request for book: {}, user: {}", bookId, user.getId());

        // 1. Verify book existence
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.READER_BOOK_NOT_FOUND));

        Long userId = user.getId();

        // 2. Validate Quotas: hourly rate limit (exclude failed attempts so users are not penalized for server/AI errors)
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long hourlyCount = imageRepository.countByUserIdAndCreatedAtAfterAndStatusNot(userId, oneHourAgo, ImageGenerationStatus.FAILED);
        if (hourlyCount >= properties.getRateLimitPerHour()) {
            log.warn("User {} exceeded hourly image generation quota: {}/{}", userId, hourlyCount, properties.getRateLimitPerHour());
            throw new ImageQuotaExceededException(ApiMessageKey.IMAGE_GEN_RATE_LIMIT_EXCEEDED);
        }

        // 3. Validate Quotas: active concurrent in-flight limit
        List<ImageGenerationStatus> inFlightStatuses = List.of(ImageGenerationStatus.QUEUED, ImageGenerationStatus.PROCESSING);
        long activeCount = imageRepository.countByUserIdAndStatusIn(userId, inFlightStatuses);
        if (activeCount >= properties.getMaxConcurrentPerUser()) {
            log.warn("User {} has {} active image generations (max: {})", userId, activeCount, properties.getMaxConcurrentPerUser());
            throw new ImageQuotaExceededException(ApiMessageKey.IMAGE_GEN_CONCURRENT_LIMIT_EXCEEDED);
        }

        // 4. Build book prompt context (Title, Author, Genre, Synopsis, Cover Page Reference)
        String authorName = (book.getCustomAuthorName() != null && !book.getCustomAuthorName().isBlank())
                ? book.getCustomAuthorName()
                : (book.getAuthor() != null ? book.getAuthor().getFullName() : null);

        String genreName = book.getMainGenre() != null ? book.getMainGenre().getNameAr() : null;

        String coverImageUrl = null;
        try {
            Optional<Attachment> cover = attachmentService.getAttachment(
                    book.getId(),
                    BookResponseBuilderService.BOOK_ENTITY_TYPE,
                    BookResponseBuilderService.COVER_IMAGE_TYPE
            );
            if (cover.isPresent()) {
                coverImageUrl = fileStorageService.getFileUrl(cover.get().getStoragePath(), UrlStrategy.SIGNED);
            }
        } catch (Exception e) {
            log.warn("Could not retrieve cover image for book {}: {}", bookId, e.getMessage());
        }

        BookPromptContext bookContext = BookPromptContext.builder()
                .title(book.getTitle())
                .author(authorName)
                .genre(genreName)
                .description(book.getDescription())
                .coverImageUrl(coverImageUrl)
                .build();

        // 5. Build prompt and compute hash
        String constructedPrompt = promptBuilder.buildPrompt(
                request.context(),
                request.theme(),
                request.aspectRatio(),
                request.styleNotes(),
                bookContext
        );
        String promptHash = promptBuilder.computePromptHash(
                request.context(),
                request.theme(),
                request.aspectRatio()
        );

        // 5. In-flight deduplication check
        boolean duplicate = imageRepository.existsInFlightDuplicate(userId, bookId, promptHash, inFlightStatuses);
        if (duplicate) {
            log.warn("In-flight duplicate image generation requested for book: {}, user: {}, hash: {}", bookId, userId, promptHash);
            throw new ImageDuplicateInFlightException(ApiMessageKey.IMAGE_GEN_IN_FLIGHT_DUPLICATE);
        }

        // 6. Persist initial entity in QUEUED status
        GeneratedImage image = new GeneratedImage();
        image.setBook(book);
        image.setUser(user);
        image.setUserContext(promptBuilder.sanitize(request.context()));
        image.setTheme(request.theme());
        image.setAspectRatio(request.aspectRatio().getRatio());
        image.setStyleNotes(request.styleNotes() != null ? promptBuilder.sanitize(request.styleNotes()) : null);
        image.setPromptHash(promptHash);
        image.setAiModel(properties.getAiModel());
        image.setStatus(ImageGenerationStatus.QUEUED);

        GeneratedImage saved = imageRepository.save(image);
        log.info("Persisted image record {} with status QUEUED for book: {}", saved.getId(), bookId);

        // 7. Dispatch asynchronous generation event
        eventPublisher.publishEvent(new ImageGenerationRequestedEvent(
                saved.getId(),
                bookId,
                userId,
                constructedPrompt,
                request.aspectRatio().getRatio(),
                0,
                book.getTitle(),
                authorName
        ));

        return new ImageStatusResponse(
                saved.getId(),
                bookId,
                saved.getStatus(),
                null,
                null,
                saved.getCreatedAt(),
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ImageStatusResponse getStatus(Long bookId, UUID imageId, User user) {
        GeneratedImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ImageNotFoundException(ApiMessageKey.IMAGE_GEN_NOT_FOUND));

        if (!image.getBook().getId().equals(bookId)) {
            throw new ImageNotFoundException(ApiMessageKey.IMAGE_GEN_NOT_FOUND);
        }

        if (!image.getUser().getId().equals(user.getId())) {
            throw new ImageForbiddenException(ApiMessageKey.IMAGE_GEN_FORBIDDEN);
        }

        String resolvedUrl = null;
        if (image.getStatus() == ImageGenerationStatus.COMPLETED && image.getStorageKey() != null) {
            resolvedUrl = storageService.resolveImageUrl(image.getStorageKey());
        }

        return new ImageStatusResponse(
                image.getId(),
                image.getBook().getId(),
                image.getStatus(),
                resolvedUrl,
                image.getFailureReason(),
                image.getCreatedAt(),
                image.getCompletedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public GenerateImageResponse getImage(Long bookId, UUID imageId, User user) {
        GeneratedImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ImageNotFoundException(ApiMessageKey.IMAGE_GEN_NOT_FOUND));

        if (!image.getBook().getId().equals(bookId)) {
            throw new ImageNotFoundException(ApiMessageKey.IMAGE_GEN_NOT_FOUND);
        }

        if (!image.getUser().getId().equals(user.getId())) {
            throw new ImageForbiddenException(ApiMessageKey.IMAGE_GEN_FORBIDDEN);
        }

        return toResponse(image);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GenerateImageResponse> listImages(Long bookId, User user, Pageable pageable) {
        if (!bookRepository.existsById(bookId)) {
            throw new ResourceNotFoundException(ApiMessageKey.READER_BOOK_NOT_FOUND);
        }

        Page<GeneratedImage> images = imageRepository.findAllByBookIdAndUserId(bookId, user.getId(), pageable);
        return images.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GenerateImageResponse> listAllReaderImages(Long bookIdFilter, User user, Pageable pageable) {
        Page<GeneratedImage> images = imageRepository.findAllByUserIdAndOptionalBookId(user.getId(), bookIdFilter, pageable);
        return images.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.doova.ktab.features.imagegen.dto.response.BookImageGroupResponse> getGroupedReaderImages(User user) {
        java.util.List<GeneratedImage> images = imageRepository.findAllByUserIdWithBook(user.getId());

        java.util.Map<Long, java.util.List<GeneratedImage>> groupedByBook = images.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        img -> img.getBook().getId(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        return groupedByBook.entrySet().stream()
                .map(entry -> {
                    Long bId = entry.getKey();
                    java.util.List<GeneratedImage> bookImages = entry.getValue();
                    String bTitle = bookImages.isEmpty() ? "Unknown" : bookImages.get(0).getBook().getTitle();
                    java.util.List<GenerateImageResponse> responseList = bookImages.stream()
                            .map(this::toResponse)
                            .toList();
                    return new com.doova.ktab.features.imagegen.dto.response.BookImageGroupResponse(
                            bId,
                            bTitle,
                            responseList.size(),
                            responseList
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.doova.ktab.features.imagegen.dto.response.ReaderBookImageSummary> getReaderBooksWithImages(User user) {
        return imageRepository.findDistinctBooksWithImageCounts(user.getId());
    }

    private GenerateImageResponse toResponse(GeneratedImage img) {
        String url = null;
        if (img.getStatus() == ImageGenerationStatus.COMPLETED && img.getStorageKey() != null) {
            url = storageService.resolveImageUrl(img.getStorageKey());
        }
        return new GenerateImageResponse(
                img.getId(),
                img.getBook().getId(),
                img.getBook().getTitle(),
                img.getUserContext(),
                img.getTheme(),
                img.getAspectRatio(),
                img.getStyleNotes(),
                url,
                img.getStatus(),
                img.getCreatedAt(),
                img.getCompletedAt()
        );
    }

    @Override
    @Transactional
    public void deleteImage(Long bookId, UUID imageId, User user) {
        GeneratedImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ImageNotFoundException(ApiMessageKey.IMAGE_GEN_NOT_FOUND));

        if (!image.getBook().getId().equals(bookId)) {
            throw new ImageNotFoundException(ApiMessageKey.IMAGE_GEN_NOT_FOUND);
        }

        if (!image.getUser().getId().equals(user.getId())) {
            throw new ImageForbiddenException(ApiMessageKey.IMAGE_GEN_FORBIDDEN);
        }

        if (image.getStorageKey() != null) {
            storageService.deleteImage(image.getStorageKey());
        }

        imageRepository.delete(image);
        log.info("Deleted generated image record {} for book {} and user {}", imageId, bookId, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public com.doova.ktab.features.imagegen.dto.response.ImageGenFiltersResponse getAvailableFilters() {
        var themes = java.util.Arrays.stream(com.doova.ktab.features.imagegen.enums.ImageTheme.values())
                .map(t -> new com.doova.ktab.features.imagegen.dto.response.ImageGenFiltersResponse.ThemeOption(
                        t.name(),
                        t.getLocalizedName(messageSource),
                        t.getLocalizedDescription(messageSource)
                ))
                .toList();

        var aspectRatios = java.util.Arrays.stream(com.doova.ktab.features.imagegen.enums.ImageAspectRatio.values())
                .map(r -> new com.doova.ktab.features.imagegen.dto.response.ImageGenFiltersResponse.AspectRatioOption(
                        r.name(),
                        r.getRatio(),
                        r.getLocalizedName(messageSource),
                        r.getLocalizedDescription(messageSource)
                ))
                .toList();

        return new com.doova.ktab.features.imagegen.dto.response.ImageGenFiltersResponse(themes, aspectRatios);
    }
}
