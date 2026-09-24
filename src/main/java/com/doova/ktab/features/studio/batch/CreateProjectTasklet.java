package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.client.dto.StudioProjectResponse;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.enums.StudioProjectLifecycle;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Phase 4 {@code createProjectStep}: persists {@code col_external_project_id} and advances
 * lifecycle to {@code CREATED} in one committed transaction, before any other Studio API
 * call is made. This is the crash-safety requirement from docs/ocr_engine_v3.md, Phase 4.2:
 *
 * <blockquote>"persist {@code col_external_project_id} and advance to {@code CREATED}
 * in one committed transaction, before anything else"</blockquote>
 *
 * <p>The presigned PDF URL is generated with a long TTL so Studio can fetch it during its
 * own parsing phase, which may be delayed. A project row that already has lifecycle
 * {@code CREATED} or later is idempotent — the step resolves the existing project id
 * and continues rather than creating a duplicate.
 */
@Slf4j
@RequiredArgsConstructor
public class CreateProjectTasklet implements Tasklet {

    /** TTL for the presigned URL supplied to Studio as the {@code from_url} parameter. */
    private static final Duration PDF_URL_TTL = Duration.ofHours(24);

    private final Long bookId;
    private final ElevenLabsStudioClient client;
    private final StudioProjectRepository projectRepository;
    private final BookRepository bookRepository;
    private final ObjectStorageService storage;
    private final AttachmentService attachmentService;
    private final StudioProperties props;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // Idempotency: if the project was already created (e.g. batch restart), skip re-creation.
        Optional<StudioProject> existing = projectRepository.findLiveByBookId(bookId);
        if (existing.isPresent() && existing.get().getLifecycle() != StudioProjectLifecycle.PENDING) {
            log.info("studio.createProject.skip bookId={} — project already exists extId={}",
                    bookId, existing.get().getExternalProjectId());
            return RepeatStatus.FINISHED;
        }

        if (props.getAudiobook().isDryRun()) {
            log.info("studio.createProject.dryRun bookId={} — skipping API call", bookId);
            return RepeatStatus.FINISHED;
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        String pdfKey = attachmentService.getAttachment(bookId, "Book", "PDF_SOURCE")
                .map(a -> a.getStoragePath())
                .orElseThrow(() -> new IllegalStateException(
                        "No PDF_SOURCE attachment for bookId=" + bookId));

        String pdfUrl = storage.generatePresignedUrl(pdfKey, PDF_URL_TTL);

        StudioProjectResponse response = client.createProject(
                book.getTitle(),
                pdfUrl,
                props.getDefaultModelId(),
                props.getDefaultTitleVoiceId(),
                props.getDefaultParagraphVoiceId()
        );

        // Persist in the SAME transaction before returning — crash after this point
        // means the reconciler will find and clean up the orphaned remote project.
        StudioProject project = existing.orElseGet(() -> {
            StudioProject p = new StudioProject();
            p.setBook(book);
            return p;
        });
        project.setExternalProjectId(response.projectId());
        project.setLifecycle(StudioProjectLifecycle.CREATED);
        project.setModelId(response.defaultModelId());
        project.setTitleVoiceId(response.defaultTitleVoiceId());
        project.setParagraphVoiceId(response.defaultParagraphVoiceId());
        project.setQualityPreset(response.qualityPreset());
        projectRepository.save(project);

        log.info("studio.createProject.done bookId={} extProjectId={}", bookId, response.projectId());
        return RepeatStatus.FINISHED;
    }
}

