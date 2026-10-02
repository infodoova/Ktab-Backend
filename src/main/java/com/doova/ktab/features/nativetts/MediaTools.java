package com.doova.ktab.features.nativetts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface MediaTools {

    int durationMs(Path mp3) throws IOException;

    /** Joins MP3 files end to end without re-encoding. */
    void concat(List<Path> parts, Path out) throws IOException;
}
