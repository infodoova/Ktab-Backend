package com.doova.ktab.features.nativetts;

import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterSynthesizerTest {

    /** Reports pre-set chunk durations and records what it was asked to join. */
    static class FakeMediaTools implements MediaTools {
        private final List<Integer> durations;
        private int next;
        final List<Path> concatenated = new ArrayList<>();

        FakeMediaTools(List<Integer> durations) {
            this.durations = durations;
        }

        @Override
        public int durationMs(Path mp3) {
            return durations.get(next++);
        }

        @Override
        public void concat(List<Path> parts, Path out) throws IOException {
            concatenated.addAll(parts);
            Files.write(out, new byte[]{9});
        }
    }

    private static NativeTtsProperties props(int maxChars, boolean context, String model) {
        NativeTtsProperties p = new NativeTtsProperties();
        p.setMaxCharsPerRequest(maxChars);
        p.setSendContext(context);
        p.setModelId(model);
        return p;
    }

    @Test
    void offsetsUseTheMeasuredDurationOfEveryChunk(@TempDir Path dir) throws Exception {
        ChunkTtsProvider tts = (text, prev, next) -> text.startsWith("أ")
                ? new ChunkAudio(new byte[]{1}, "أب", new double[]{0.0, 0.4}, new double[]{0.4, 0.9})
                : new ChunkAudio(new byte[]{2}, "جد", new double[]{0.0, 0.3}, new double[]{0.3, 0.7});
        FakeMediaTools media = new FakeMediaTools(List.of(1000, 800));

        ChapterAudio a = new ChapterSynthesizer(tts, media, props(2, true, "eleven_multilingual_v2")).synthesize("أب جد", dir);

        assertThat(a.chars()).isEqualTo("أب جد");
        assertThat(a.startMs()).containsExactly(0, 400, 1000, 1000, 1300);
        assertThat(a.endMs()).containsExactly(400, 900, 1000, 1300, 1700);
        assertThat(a.durationMs()).isEqualTo(1800);
        assertThat(media.concatenated).hasSize(2);
        assertThat(Files.exists(a.mp3())).isTrue();
    }

    @Test
    void passesTheNeighbouringTextForContinuity(@TempDir Path dir) throws Exception {
        List<String[]> calls = new ArrayList<>();
        ChunkTtsProvider tts = (t, p, n) -> {
            calls.add(new String[]{t, p, n});
            return new ChunkAudio(new byte[]{1}, t, new double[t.length()], new double[t.length()]);
        };

        new ChapterSynthesizer(tts, new FakeMediaTools(List.of(100, 100)), props(2, true, "eleven_multilingual_v2"))
                .synthesize("أب جد", dir);

        assertThat(calls.get(0)).containsExactly("أب", "", "جد");
        assertThat(calls.get(1)).containsExactly("جد", "أب", "");
    }

    @Test
    void v3SendsNoContextAndKeepsChunksUnderItsLimit(@TempDir Path dir) throws Exception {
        List<String[]> calls = new ArrayList<>();
        ChunkTtsProvider tts = (t, p, n) -> {
            calls.add(new String[]{t, p, n});
            return new ChunkAudio(new byte[]{1}, t, new double[t.length()], new double[t.length()]);
        };
        String text = ("جملة قصيرة. ").repeat(900).strip(); // ~10,800 chars

        NativeTtsProperties p = props(9000, true, "eleven_v3");
        List<String> chunks = new ChapterSynthesizer(tts, new FakeMediaTools(java.util.Collections.nCopies(10, 100)), p)
                .chunksFor(text);

        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(4500));
        new ChapterSynthesizer(tts, new FakeMediaTools(java.util.Collections.nCopies(10, 100)), p).synthesize(text, dir);
        assertThat(calls).isNotEmpty().allSatisfy(c -> {
            assertThat(c[1]).as("previous_text").isEmpty();
            assertThat(c[2]).as("next_text").isEmpty();
        });
    }

    @Test
    void aBlankChapterIsAnErrorNotSilentAudio(@TempDir Path dir) {
        ChapterSynthesizer s = new ChapterSynthesizer((t, p, n) -> null, new FakeMediaTools(List.of()),
                props(100, true, "eleven_multilingual_v2"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> s.synthesize("  ", dir))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no text");
    }
}
