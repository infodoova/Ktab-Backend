package com.doova.ktab.features.studio.web;

import com.doova.ktab.features.audiobook.AudiobookLauncher;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.model.BookAudioChapter;
import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.BookAudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Audiobook generation for a book. The path stays /api/studio so the frontend does not change: while
 * KTAB_STUDIO_ENABLED is true this runs ElevenLabs Studio (docs/ocr_engine_v3.md, Phase 4), otherwise Ktab's own TTS.
 * Requires an explicit admin or admin librarian trigger (Phase 4.4 cost guard).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN')")
@Slf4j
@Tag(name = "Studio Audiobook", description = "Endpoints for managing audiobook generation (Studio or Ktab's own TTS)")
public class StudioJobController {

    private final AudiobookLauncher audiobookLauncher;
    private final BookRepository bookRepository;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final BookAudioChapterRepository audioChapterRepository;
    private final StudioProperties studioProperties;
    private final MeterRegistry meterRegistry;

    private String pipeline() {
        return studioProperties.isEnabled() ? "STUDIO" : "NATIVE";
    }

    /**
     * Launch the audiobook pipeline for a book. Can be invoked for books from any ingestion route.
     */
    @Operation(summary = "Start audiobook generation for a book")
    @PostMapping("/books/{bookId}/audiobook")
    public ResponseEntity<Map<String, Object>> startAudiobook(@PathVariable Long bookId) throws Exception {
        bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        Optional<JobExecution> running = audiobookLauncher.runningFor(bookId);
        if (running.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "RUNNING",
                    "executionId", running.get().getId(),
                    "message", "Audiobook generation is already running for book " + bookId
            ));
        }

        JobExecution execution = audiobookLauncher.launch(bookId);
        meterRegistry.counter("studio.jobs", "status", "started", "pipeline", pipeline()).increment();
        log.info("Started {} for bookId={}, executionId={}", audiobookLauncher.activeJobName(), bookId, execution.getId());

        return ResponseEntity.accepted().body(Map.of(
                "executionId", execution.getId(),
                "bookId", bookId,
                "status", execution.getStatus().toString(),
                "pipeline", pipeline()
        ));
    }

    /**
     * Audiobook status for a book: the Studio project and chapters while Studio is enabled, otherwise the chapters Ktab's
     * own TTS has produced and whether a job is running.
     */
    @Operation(summary = "Get audiobook conversion status for a book")
    @GetMapping("/books/{bookId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("bookId", bookId);
        resp.put("hasAudio", Boolean.TRUE.equals(book.getHasAudio()));
        resp.put("pipeline", pipeline());

        if (!studioProperties.isEnabled()) {
            resp.put("running", audiobookLauncher.runningFor(bookId).isPresent());
            List<Map<String, Object>> chapters = audioChapterRepository.findByBook_IdOrderBySortOrderAsc(bookId).stream()
                    .map(StudioJobController::nativeChapter).toList();
            resp.put("chaptersCount", chapters.size());
            resp.put("chapters", chapters);
            return ResponseEntity.ok(resp);
        }

        Optional<StudioProject> projectOpt = projectRepository.findLiveByBookId(bookId);

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

    private static Map<String, Object> nativeChapter(BookAudioChapter c) {
        Map<String, Object> cm = new LinkedHashMap<>();
        cm.put("sortOrder", c.getSortOrder());
        cm.put("title", c.getBookSection() == null ? null : c.getBookSection().getTitle());
        cm.put("durationMs", c.getDurationMs());
        return cm;
    }
}
