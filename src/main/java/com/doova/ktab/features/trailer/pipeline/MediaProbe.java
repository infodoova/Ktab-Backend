package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Ktab's own ffprobe — the agent's QC is not trusted alone (D7, Review Focus 4). */
@Component
public class MediaProbe {

    public record Media(double seconds, int width, int height) {
    }

    private final TrailerProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public MediaProbe(TrailerProperties properties) {
        this.properties = properties;
    }

    public Media probe(Path file) {
        String binary = resolveBinary();
        try {
            Process p = new ProcessBuilder(binary, "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height:format=duration", "-of", "json", file.toString())
                    .redirectErrorStream(true).start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IllegalStateException("ffprobe failed: " + new String(out, StandardCharsets.UTF_8));
            }
            JsonNode root = json.readTree(out);
            JsonNode stream = root.path("streams").path(0);
            return new Media(root.path("format").path("duration").asDouble(), stream.path("width").asInt(),
                    stream.path("height").asInt());
        } catch (IOException e) {
            throw new IllegalStateException("ffprobe unavailable (is ffmpeg installed? binary=" + binary + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String resolveBinary() {
        String configured = properties.getFfprobePath();
        if (configured == null || configured.isBlank()) {
            configured = "ffprobe";
        }
        if (java.nio.file.Files.exists(Path.of(configured))) {
            return configured;
        }
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                Path wingetLinks = Path.of(localAppData, "Microsoft", "WinGet", "Links", "ffprobe.exe");
                if (java.nio.file.Files.exists(wingetLinks)) {
                    return wingetLinks.toString();
                }
                Path wingetPkgs = Path.of(localAppData, "Microsoft", "WinGet", "Packages");
                if (java.nio.file.Files.exists(wingetPkgs)) {
                    try (java.util.stream.Stream<Path> stream = java.nio.file.Files.find(wingetPkgs, 4,
                            (p, attrs) -> p.getFileName().toString().equalsIgnoreCase("ffprobe.exe"))) {
                        var found = stream.findFirst();
                        if (found.isPresent()) {
                            return found.get().toString();
                        }
                    } catch (IOException ignored) { }
                }
            }
        }
        return configured;
    }
}
