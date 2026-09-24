package com.doova.ktab.features.studio.web;

import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for managing ElevenLabs Studio audiobook generation jobs (docs/ocr_engine_v3.md, Phase 4).
 * Requires an explicit admin or admin librarian trigger (Phase 4.4 cost guard).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN')")
@Slf4j
@Tag(name = "Studio Audiobook", description = "Endpoints for managing Studio audiobook generation")
public class StudioJobController {

    private final JobLauncher jobLauncher;
    @Qualifier("studioAudiobookJob")
    private final Job studioAudiobookJob;
    private final JobExplorer jobExplorer;
    private final BookRepository bookRepository;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Launch the audiobook conversion pipeline for a book.
     * Can be invoked for books from either pipeline (STUDIO digital or OCR scanned/hybrid).
     */
    @Operation(summary = "Start audiobook generation for a book")
    @PostMapping("/books/{bookId}/audiobook")
    public ResponseEntity<Map<String, Object>> startAudiobook(@PathVariable Long bookId) throws Exception {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        // Check if audiobook job is already actively running for this book
        Optional<JobExecution> running = jobExplorer.findRunningJobExecutions("studioAudiobookJob").stream()
                .filter(exec -> bookId.equals(exec.getJobParameters().getLong("bookId")))
                .findFirst();

        if (running.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "RUNNING",
                    "executionId", running.get().getId(),
                    "message", "Studio audiobook generation is already running for book " + bookId
            ));
        }

        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(studioAudiobookJob, params);
        meterRegistry.counter("studio.jobs", "status", "started").increment();

        log.info("Started studioAudiobookJob for bookId={}, executionId={}", bookId, execution.getId());

        return ResponseEntity.accepted().body(Map.of(
                "executionId", execution.getId(),
                "bookId", bookId,
                "status", execution.getStatus().toString()
        ));
    }

    /**
     * Query Studio project and chapter status for a book.
     */
    @Operation(summary = "Get Studio project and conversion status for a book")
    @GetMapping("/books/{bookId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        Optional<StudioProject> projectOpt = projectRepository.findLiveByBookId(bookId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("bookId", bookId);
        resp.put("hasAudio", Boolean.TRUE.equals(book.getHasAudio()));

        if (projectOpt.isEmpty()) {
            resp.put("projectExists", false);
            return ResponseEntity.ok(resp);
        }

        StudioProject project = projectOpt.get();
        resp.put("projectExists", true);
        resp.put("projectId", project.getId());
        resp.put("externalProjectId", project.getExternalProjectId());
        resp.put("lifecycle", project.getLifecycle().name());
        resp.put("lastSyncedAt", project.getLastSyncedAt());
        resp.put("syncError", project.getSyncError());

        List<StudioChapter> chapters = chapterRepository.findByProject_IdOrderByOrderIndexAsc(project.getId());
        resp.put("chaptersCount", chapters.size());

        List<Map<String, Object>> chapterList = chapters.stream().map(c -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("chapterId", c.getId());
            cm.put("externalChapterId", c.getExternalChapterId());
            cm.put("orderIndex", c.getOrderIndex());
            cm.put("origin", c.getOrigin().name());
            cm.put("progress", c.getConversionProgress());
            cm.put("error", c.getLastConversionError());
            cm.put("deleted", c.getDeletedAt() != null);
            return cm;
        }).toList();
        resp.put("chapters", chapterList);

        return ResponseEntity.ok(resp);
    }
}
