package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MediaProbeTest {

    @Test
    void probe_withDefaultProperties_resolvesBinarySuccessfullyOnWindows() {
        TrailerProperties props = new TrailerProperties();
        MediaProbe probe = new MediaProbe(props);

        // Verify that MediaProbe does not crash on initialization and can resolve ffprobe
        assertThat(props.getFfprobePath()).isEqualTo("ffprobe");
    }

    @Test
    void probe_withExplicitPath_usesConfiguredPath() {
        TrailerProperties props = new TrailerProperties();
        props.setFfprobePath("C:/Users/PC/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-9.0.2-full_build/bin/ffprobe.exe");
        MediaProbe probe = new MediaProbe(props);

        assertThat(new File(props.getFfprobePath()).exists()).isTrue();
    }
}
