package com.doova.ktab.features.nativetts;

import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegMediaTools implements MediaTools {

    private static final long TIMEOUT_SECONDS = 180;

    private final NativeTtsProperties props;

    public FfmpegMediaTools(NativeTtsProperties props) {
        this.props = props;
    }

    @Override
    public int durationMs(Path mp3) throws IOException {
        String out = run(List.of(props.getFfprobePath(), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                mp3.toAbsolutePath().toString())).strip();
        try {
            return (int) Math.round(Double.parseDouble(out) * 1000.0);
        } catch (NumberFormatException e) {
            throw new IOException("ffprobe returned no duration for " + mp3 + ": " + out, e);
        }
    }

    @Override
    public void concat(List<Path> parts, Path out) throws IOException {
        Path list = out.resolveSibling(out.getFileName() + ".list.txt");
        StringBuilder sb = new StringBuilder();
        for (Path p : parts) {
            sb.append("file '").append(p.toAbsolutePath().toString().replace('\\', '/').replace("'", "'\\''")).append("'\n");
        }
        Files.writeString(list, sb.toString(), StandardCharsets.UTF_8);
        try {
            run(List.of(props.getFfmpegPath(), "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i",
                    list.toAbsolutePath().toString(), "-c", "copy", out.toAbsolutePath().toString()));
        } finally {
            Files.deleteIfExists(list);
        }
    }

    private static String run(List<String> command) throws IOException {
        Process p = new ProcessBuilder(new ArrayList<>(command)).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException(command.get(0) + " timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(command.get(0) + " was interrupted", e);
        }
        if (p.exitValue() != 0) {
            throw new IOException(command.get(0) + " failed (" + p.exitValue() + "): " + output);
        }
        return output;
    }
}
