package com.doova.ktab.features.nativetts;

import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the real ffmpeg/ffprobe when they are on the PATH (they are in the Docker image); skipped otherwise. */
class FfmpegMediaToolsTest {

    private static boolean ffmpegAvailable() {
        try {
            return new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start().waitFor() == 0
                    && new ProcessBuilder("ffprobe", "-version").redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void silence(Path out, double seconds) throws Exception {
        Process p = new ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono", "-t",
                Double.toString(seconds), "-b:a", "128k", out.toString()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertThat(p.waitFor()).isZero();
    }

    @Test
    void measuresAndJoinsRealMp3s(@TempDir Path dir) throws Exception {
        assumeTrue(ffmpegAvailable(), "ffmpeg not installed");
        Path a = dir.resolve("a.mp3");
        Path b = dir.resolve("b.mp3");
        silence(a, 1.0);
        silence(b, 2.0);
        MediaTools tools = new FfmpegMediaTools(new NativeTtsProperties());

        int da = tools.durationMs(a);
        int db = tools.durationMs(b);
        assertThat(da).isBetween(950, 1100);
        assertThat(db).isBetween(1950, 2100);

        Path out = dir.resolve("joined.mp3");
        tools.concat(List.of(a, b), out);

        assertThat(Files.size(out)).isPositive();
        assertThat(tools.durationMs(out)).isBetween(da + db - 80, da + db + 80);
    }
}
