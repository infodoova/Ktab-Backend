package com.doova.ktab.features.nativetts;

import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One chapter of text in, one MP3 and one character-level timing index out. Each chunk's timings are shifted by the
 * <em>measured</em> length of the chunks before it (not the vendor's estimate), so the highlight never drifts.
 */
@Component
public class ChapterSynthesizer {

    /** eleven_v3 rejects requests over 5,000 characters; stay under it. */
    private static final int V3_MAX_CHARS = 4500;

    private final ChunkTtsProvider tts;
    private final MediaTools media;
    private final NativeTtsProperties props;

    public ChapterSynthesizer(ChunkTtsProvider tts, MediaTools media, NativeTtsProperties props) {
        this.tts = tts;
        this.media = media;
        this.props = props;
    }

    public List<String> chunksFor(String text) {
        int max = props.isV3() ? Math.min(props.getMaxCharsPerRequest(), V3_MAX_CHARS) : props.getMaxCharsPerRequest();
        return TextChunker.chunk(text, max);
    }

    public ChapterAudio synthesize(String chapterText, Path workDir) {
        List<String> chunks = chunksFor(chapterText);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("The chapter has no text to read");
        }
        boolean context = props.isSendContext() && !props.isV3();
        try {
            Files.createDirectories(workDir);
            List<Path> parts = new ArrayList<>();
            StringBuilder chars = new StringBuilder();
            List<Integer> starts = new ArrayList<>();
            List<Integer> ends = new ArrayList<>();
            int offset = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String previous = context && i > 0 ? chunks.get(i - 1) : "";
                String next = context && i < chunks.size() - 1 ? chunks.get(i + 1) : "";
                ChunkAudio a = tts.synthesize(chunks.get(i), previous, next);
                if (a.chars().length() != a.startSec().length || a.chars().length() != a.endSec().length) {
                    throw new IllegalStateException("The vendor's timings do not match its characters for chunk " + (i + 1));
                }
                Path part = workDir.resolve(String.format("part-%04d.mp3", i));
                Files.write(part, a.mp3());
                int measured = media.durationMs(part);
                if (i > 0) { // the space between two chunks sits exactly on the boundary
                    chars.append(' ');
                    starts.add(offset);
                    ends.add(offset);
                }
                chars.append(a.chars());
                for (int c = 0; c < a.startSec().length; c++) {
                    starts.add(offset + (int) Math.round(a.startSec()[c] * 1000.0));
                    ends.add(offset + (int) Math.round(a.endSec()[c] * 1000.0));
                }
                parts.add(part);
                offset += measured;
            }
            Path mp3 = workDir.resolve("chapter.mp3");
            media.concat(parts, mp3);
            return new ChapterAudio(mp3, offset, chars.toString(),
                    starts.stream().mapToInt(Integer::intValue).toArray(), ends.stream().mapToInt(Integer::intValue).toArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
