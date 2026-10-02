package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway.SessionFile;
import com.doova.ktab.features.trailer.agent.TrailerTask;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.config.TrailerVoice;
import com.doova.ktab.features.trailer.endcard.EndCardRenderer;
import com.doova.ktab.features.trailer.endcard.EndCardSpec;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** QUEUED -> RUNNING: upload the PDF, render and upload the end-card layers, create the session (outcome kickoff + budget in one call). */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerLauncher {

    private static final String LOGO_RESOURCE = "trailer/endcard/ktab-logo.png";

    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final TrailerBookSource books;
    private final TrailerProperties properties;
    private final EndCardRenderer endCardRenderer;

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
        List<TrailerVoice> voices = properties.voiceCatalog();
        if (voices.isEmpty()) {
            fail(t, "No narration voices are configured (KTAB_TRAILER_VOICES).");
            return;
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("trailer-" + trailerId + "-");
            boolean needsBookUpload = t.getBookFileId() == null;
            boolean needsCoverUpload = facts.coverKey() != null && t.getCoverFileId() == null;

            if (needsBookUpload) {
                Path pdf = books.downloadPdf(facts.pdfKey(), dir.resolve("book.pdf"));
                t.setBookFileId(gateway.uploadBook(pdf));
            }
            // The cover is needed locally on every launch (a retry included): the end card is rendered from it.
            Path cover = facts.coverKey() == null ? null : books.downloadCover(facts.coverKey(), dir.resolve("cover.jpg"));
            if (needsCoverUpload) {
                t.setCoverFileId(gateway.uploadFile(cover));
            }
            if (needsBookUpload || needsCoverUpload) {
                t = trailers.save(t); // a retry after a crash reuses the uploaded files
            }

            List<SessionFile> files = new ArrayList<>();
            boolean hasCover = t.getCoverFileId() != null;
            if (hasCover) {
                files.add(new SessionFile(t.getCoverFileId(), "/workspace/cover.jpg"));
            }
            for (Path layer : endCardRenderer.render(
                    new EndCardSpec(facts.title(), facts.author(), cover, copyLogo(dir)), dir.resolve("endcard"))) {
                files.add(new SessionFile(gateway.uploadFile(layer), "/workspace/endcard/" + layer.getFileName()));
            }

            String sessionId = gateway.startSession(trailerId, t.getBookFileId(), files,
                    TrailerTask.describe(facts.title(), facts.author(), facts.language(),
                            properties.getMaxVideoJobs(), properties.getMaxHiggsfieldGenerations(),
                            properties.getHiggsfieldMaxInFlight(), properties.getHiggsfieldGenerateArgs(), hasCover,
                            voices),
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

    private static Path copyLogo(Path dir) throws IOException {
        Path logo = dir.resolve("ktab-logo.png");
        try (InputStream in = new ClassPathResource(LOGO_RESOURCE).getInputStream()) {
            Files.copy(in, logo);
        }
        return logo;
    }

    private void fail(BookTrailer t, String reason) {
        t.setStatus(TrailerStatus.FAILED);
        t.setError(reason);
        t.setFinishedAt(Instant.now());
        trailers.save(t);
    }
}
