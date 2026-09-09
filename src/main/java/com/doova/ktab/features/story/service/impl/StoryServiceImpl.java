package com.doova.ktab.features.story.service.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.exception.S3UploadException;
import com.doova.ktab.features.story.dto.CreateStoryRequest;
import com.doova.ktab.features.story.dto.UpdateStoryRequest;
import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.model.StoryConstitution;
import com.doova.ktab.features.story.repository.SessionRepository;
import com.doova.ktab.features.story.repository.StoryRepository;
import com.doova.ktab.features.story.service.StoryService;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.utils.validator.ImageValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@RequiredArgsConstructor
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final SessionRepository sessionRepository;
    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;
    private final ImageValidator imageValidator;

    private static final String STORY_ENTITY_TYPE = Story.class.getSimpleName();
    private static final String COVER_IMAGE_TYPE = "COVER_IMAGE";

    @Override
    @Transactional
    public Story createStory(CreateStoryRequest request, MultipartFile coverImage, User author) {
        var c = request.constitution();
        StoryConstitution constitution = new StoryConstitution(c.settingTime(), c.settingPlace(), c.coreTheme(), c.tone(), c.philosophy(), c.mainConflict(), c.forbiddenElements(), c.pacing());
        StoryVisualStyle storyVisualStyle = StoryVisualStyle.valueOfSafe(request.visualStyle());
        Story story = new Story(author, request.title(), request.genre(), request.maxScenes(), request.lens(), constitution, storyVisualStyle, request.visualStyleNotes());
        Story savedStory = storyRepository.save(story);

        // Handle cover image upload if provided
        if (coverImage != null && !coverImage.isEmpty()) {
            handleCoverImageUpload(savedStory, coverImage);
        }

        return savedStory;
    }

    private void handleCoverImageUpload(Story story, MultipartFile coverImage) {
        // Get existing cover if any
        Optional<Attachment> existingCover = attachmentService.getAttachment(story.getId(), STORY_ENTITY_TYPE, COVER_IMAGE_TYPE);
        String oldCoverKey = existingCover.map(Attachment::getStoragePath).orElse(null);

        String newCoverPath = null;
        List<String> keysToDeleteAfterCommit = new ArrayList<>();
        List<String> keysToDeleteOnRollback = new ArrayList<>();

        registerS3CleanupSynchronization(keysToDeleteAfterCommit, keysToDeleteOnRollback);

        Long authorId = story.getAuthor().getId();
        String coverDirectory = "stories/cover/" + authorId;

        try {
            newCoverPath = fileStorageService.storeFile(coverImage, coverDirectory);
            keysToDeleteOnRollback.add(newCoverPath);
            if (oldCoverKey != null) {
                keysToDeleteAfterCommit.add(oldCoverKey);
            }
        } catch (Exception e) {
            throw new S3UploadException(ApiMessageKey.FILE_UPLOAD_FAILED);
        }

        upsertAttachment(story, existingCover, coverImage.getOriginalFilename(), newCoverPath, coverImage.getContentType(), coverImage.getSize());
    }

    @Override
    public String getCoverImageUrl(Long storyId) {
        Optional<Attachment> cover = attachmentService.getAttachment(storyId, STORY_ENTITY_TYPE, COVER_IMAGE_TYPE);
        return cover.map(attachment -> fileStorageService.getFileUrl(attachment.getStoragePath(), UrlStrategy.SIGNED)).orElse(null);
    }

    private void upsertAttachment(Story story, Optional<Attachment> existing, String originalFileName, String storagePath, String mimeType, Long fileSize) {
        Attachment attachment = existing.orElseGet(() -> Attachment.builder().entityId(story.getId()).entityType(STORY_ENTITY_TYPE).type(COVER_IMAGE_TYPE).user(story.getAuthor()).build());

        attachment.setFileName(originalFileName);
        attachment.setStoragePath(storagePath);
        attachment.setMimeType(mimeType);
        attachment.setFileSize(fileSize);
        attachment.setSourceUrl(null);

        attachmentService.save(attachment);
    }

    private void registerS3CleanupSynchronization(List<String> keysToDeleteAfterCommit, List<String> keysToDeleteOnRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keysToDeleteAfterCommit.forEach(fileStorageService::deleteFile);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    keysToDeleteOnRollback.forEach(fileStorageService::deleteFile);
                }
            }
        });
    }

    @Override
    public Story getStoryById(Long id) {
        return storyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException(ApiMessageKey.STORY_NOT_FOUND.getKey()));
    }

    @Override
    public Page<Story> getAllStoriesPaged(Pageable pageable) {
        return storyRepository.findAll(pageable);
    }

    @Override
    public Page<Story> getStoriesByAuthor(Long authorId, Pageable pageable) {
        return storyRepository.findAllByAuthorId(authorId, pageable);
    }

    @Override
    @Transactional
    public Story updateStory(Long storyId, UpdateStoryRequest request, MultipartFile coverImage, User author) {
        Story story = storyRepository.findById(storyId).orElseThrow(() -> new IllegalArgumentException(ApiMessageKey.STORY_NOT_FOUND.getKey()));

        // Check if story has any reading sessions
        if (sessionRepository.existsByStoryId(storyId)) {
            throw new ResponseStatusException(CONFLICT, ApiMessageKey.STORY_HAS_SESSIONS.getKey());
        }

        // Verify author owns the story
        if (!story.getAuthor().getId().equals(author.getId())) {
            throw new AccessDeniedException(ApiMessageKey.STORY_NOT_OWNER.getKey());
        }

        // Update fields if provided
        if (request.title() != null && !request.title().isBlank()) {
            story.setTitle(request.title());
        }
        if (request.genre() != null) {
            story.setGenre(request.genre());
        }
        if (request.maxScenes() != null && request.maxScenes() > 0) {
            story.setSceneCount(request.maxScenes());
        }
        if (request.lens() != null) {
            story.setLens(request.lens());
        }
        if (request.constitution() != null) {
            var c = request.constitution();
            StoryConstitution constitution = new StoryConstitution(c.settingTime(), c.settingPlace(), c.coreTheme(), c.tone(), c.philosophy(), c.mainConflict(), c.forbiddenElements(), c.pacing());
            story.setConstitution(constitution);
        }

        Story savedStory = storyRepository.save(story);

        // Handle cover image upload if provided
        if (coverImage != null && !coverImage.isEmpty()) {
            handleCoverImageUpload(savedStory, coverImage);
        }

        return savedStory;
    }

    @Override
    @Transactional
    public void deleteStory(Long storyId, User author) {
        Story story = storyRepository.findById(storyId).orElseThrow(() -> new IllegalArgumentException(ApiMessageKey.STORY_NOT_FOUND.getKey()));

        // Check if story has any reading sessions
        if (sessionRepository.existsByStoryId(storyId)) {
            throw new IllegalStateException(ApiMessageKey.STORY_HAS_SESSIONS.getKey());
        }

        // Verify author owns the story
        if (!story.getAuthor().getId().equals(author.getId())) {
            throw new IllegalStateException(ApiMessageKey.STORY_NOT_OWNER.getKey());
        }

        // Delete attachments (cover image)
        List<Attachment> attachments = attachmentService.getAllAttachmentsForEntity(storyId, STORY_ENTITY_TYPE);
        List<String> keysToDeleteAfterCommit = attachments.stream().map(Attachment::getStoragePath).filter(Objects::nonNull).filter(path -> !path.isBlank()).collect(Collectors.toList());

        registerS3CleanupSynchronization(keysToDeleteAfterCommit, List.of());

        for (Attachment attachment : attachments) {
            attachmentService.delete(attachment.getId());
        }

        storyRepository.delete(story);
    }
}
