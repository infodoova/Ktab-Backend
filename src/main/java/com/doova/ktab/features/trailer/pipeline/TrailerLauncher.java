package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.agent.TrailerTask;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** QUEUED -> RUNNING: upload the PDF, create the session (outcome kickoff + budget in one call). */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerLauncher {

    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final TrailerBookSource books;
    private final TrailerProperties properties;

    public void launch(Long trailerId) {
        BookTrailer t = trailers.findById(trailerId).orElseThrow();
        if (t.getStatus() != TrailerStatus.QUEUED) {
            return;
        }
        TrailerBookSource.BookFacts facts = books.facts(t.getBookId());
        if (facts.pdfKey() == null) {
            fail(t, "The book has no source PDF to read.");
            return;
        }
        Path dir = null;
        try {
            boolean needsBookUpload = t.getBookFileId() == null;
            boolean needsCoverUpload = facts.coverKey() != null && t.getCoverFileId() == null;

            if (needsBookUpload || needsCoverUpload) {
                dir = Files.createTempDirectory("trailer-" + trailerId + "-");
            }
            if (needsBookUpload) {
                Path pdf = books.downloadPdf(facts.pdfKey(), dir.resolve("book.pdf"));
                t.setBookFileId(gateway.uploadBook(pdf));
            }
            if (needsCoverUpload) {
                Path cover = books.downloadCover(facts.coverKey(), dir.resolve("cover.jpg"));
                t.setCoverFileId(gateway.uploadCover(cover));
            }
            if (needsBookUpload || needsCoverUpload) {
                t = trailers.save(t); // a retry after a crash reuses the uploaded files
            }
            boolean hasCover = t.getCoverFileId() != null;
            String sessionId = gateway.startSession(trailerId, t.getBookFileId(), t.getCoverFileId(),
                    TrailerTask.describe(facts.title(), facts.author(), facts.language(), properties.getVoiceId(),
                            properties.getMaxVideoJobs(), properties.getMaxHiggsfieldGenerations(),
                            properties.getHiggsfieldMaxInFlight(),
                            properties.getHiggsfieldGenerateArgs(), hasCover),
                    TrailerTask.rubric());
            t.setSessionId(sessionId);
            t.setAgentVersion(properties.getAgentVersion());
            t.setStatus(TrailerStatus.RUNNING);
            t.setStartedAt(Instant.now());
            t.setNextCheckAt(Instant.now().plus(properties.getReconcileEvery()));
            trailers.save(t);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } finally {
            if (dir != null) {
                org.springframework.util.FileSystemUtils.deleteRecursively(dir.toFile());
            }
        }
    }

    private void fail(BookTrailer t, String reason) {
        t.setStatus(TrailerStatus.FAILED);
        t.setError(reason);
        t.setFinishedAt(Instant.now());
        trailers.save(t);
    }
}
