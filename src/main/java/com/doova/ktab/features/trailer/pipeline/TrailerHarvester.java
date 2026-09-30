package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** HARVESTING -> READY / NEEDS_REVIEW / FAILED. Downloads outputs, verifies them independently, stores them in R2. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerHarvester {

    static final Map<String, String> TYPES = Map.of(
            "trailer.mp4", "video/mp4",
            "trailer_clean.mp4", "video/mp4",
            "captions_ar.srt", "application/x-subrip",
            "script.md", "text/markdown",
            "qc_report.json", "application/json");
    private static final Pattern CUE_TIME = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3}) --> (\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})");

    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final TrailerStore store;
    private final MediaProbe probe;
    private final TrailerProperties properties;
    private final TrailerNotifier notifier;
    private final ObjectMapper json = new ObjectMapper();

    public void harvest(Long trailerId) {
        BookTrailer t = trailers.findById(trailerId).orElseThrow();
        if (t.getStatus() != TrailerStatus.HARVESTING) {
            return;
        }
        Map<String, TrailerAgentGateway.OutputFile> outputs = gateway.outputs(t.getSessionId()).stream()
                .filter(f -> TYPES.containsKey(f.filename()))
                .collect(Collectors.toMap(TrailerAgentGateway.OutputFile::filename, Function.identity(), (a, b) -> b));
        if (!outputs.containsKey("trailer.mp4")) {
            finish(t, TrailerStatus.FAILED, "The agent finished without producing trailer.mp4 ("
                    + t.getOutcomeResult() + (t.getOutcomeExplanation() == null ? "" : ": " + t.getOutcomeExplanation()) + ").");
            return;
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("trailer-harvest-" + trailerId + "-");
            for (TrailerAgentGateway.OutputFile f : outputs.values()) {
                Path local = dir.resolve(f.filename());
                gateway.download(f.id(), local);
                String key = TrailerStore.key(t.getBookId(), t.getId(), f.filename());
                store.upload(key, local, TYPES.get(f.filename()));
                switch (f.filename()) {
                    case "trailer.mp4" -> t.setVideoKey(key);
                    case "trailer_clean.mp4" -> t.setCleanVideoKey(key);
                    case "captions_ar.srt" -> t.setCaptionsKey(key);
                    case "qc_report.json" -> t.setQcReportJson(Files.readString(local));
                    default -> { }
                }
            }
            List<String> problems = verify(dir, t);
            if (problems.isEmpty()) {
                finish(t, TrailerStatus.READY, null);
            } else {
                finish(t, TrailerStatus.NEEDS_REVIEW, String.join(" ", problems));
            }
            gateway.archive(t.getSessionId());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (dir != null) {
                FileSystemUtils.deleteRecursively(dir.toFile());
            }
        }
    }

    private List<String> verify(Path dir, BookTrailer t) throws IOException {
        List<String> problems = new ArrayList<>();
        boolean autoPromote = properties.isAutoPromoteValidMediaWithoutQc();

        if (!"satisfied".equals(t.getOutcomeResult()) && !autoPromote) {
            problems.add("The grader result was " + t.getOutcomeResult() + ".");
        }
        MediaProbe.Media m = probe.probe(dir.resolve("trailer.mp4"));
        if (Math.abs(m.seconds() - 30.0) > 0.5) {
            problems.add(String.format(java.util.Locale.ROOT, "Duration is %.1fs, not 30s.", m.seconds()));
        }
        if (m.width() != 1920 || m.height() != 1080) {
            problems.add("Resolution is " + m.width() + "x" + m.height() + ", not 1920x1080.");
        }
        Path srt = dir.resolve("captions_ar.srt");
        if (!Files.exists(srt)) {
            problems.add("captions_ar.srt is missing.");
        } else {
            problems.addAll(checkSrt(Files.readString(srt)));
        }
        Path qc = dir.resolve("qc_report.json");
        JsonNode report = Files.exists(qc) ? json.readTree(qc.toFile()) : null;
        if (report == null) {
            if (!autoPromote) {
                problems.add("qc_report.json is missing or not ok.");
            } else {
                log.warn("trailer {} verified with ffprobe (30s, 1080p, captions ok), but qc_report.json was missing; auto-promoting", t.getId());
            }
        } else if (!"ok".equals(report.path("status").asText())) {
            problems.add("qc_report.json is missing or not ok.");
        } else if (report.path("frame_check").path("text_found").asBoolean(false)) {
            problems.add("The agent's frame check found text on screen.");
        } else if (!"ar".equals(report.path("captions").path("language").asText())) {
            problems.add("The burned-in captions are not reported as Arabic (R4).");
        }
        return problems;
    }

    static List<String> checkSrt(String srt) {
        List<String> problems = new ArrayList<>();
        Matcher m = CUE_TIME.matcher(srt);
        int cues = 0;
        while (m.find()) {
            cues++;
            double end = Integer.parseInt(m.group(5)) * 3600 + Integer.parseInt(m.group(6)) * 60
                    + Integer.parseInt(m.group(7)) + Integer.parseInt(m.group(8)) / 1000.0;
            if (end > 30.5) {
                problems.add("A caption ends at " + end + "s, after the trailer.");
                break;
            }
        }
        if (cues == 0) {
            problems.add("captions_ar.srt has no cues.");
        }
        return problems;
    }

    private void finish(BookTrailer t, TrailerStatus status, String error) {
        t.setStatus(status);
        t.setError(error);
        t.setFinishedAt(Instant.now());
        trailers.save(t);
        notifier.notifyCompletion(t);
    }
}
