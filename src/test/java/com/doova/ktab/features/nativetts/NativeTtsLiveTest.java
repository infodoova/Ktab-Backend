package com.doova.ktab.features.nativetts;

import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real ElevenLabs call (costs a few hundred characters of credit). Run:
 * NATIVE_TTS_LIVE=true NATIVE_TTS_LIVE_VOICE=&lt;voice id&gt; ELEVENLABS_API_KEY=... ./mvnw test -Dtest=NativeTtsLiveTest
 */
@EnabledIfEnvironmentVariable(named = "NATIVE_TTS_LIVE", matches = "true")
class NativeTtsLiveTest {

    @Test
    void twoParagraphsBecomeOneMp3WithTimingsThatMatchItsLength(@TempDir Path dir) throws Exception {
        NativeTtsProperties props = new NativeTtsProperties();
        props.setApiKey(System.getenv("ELEVENLABS_API_KEY"));
        props.setVoiceId(System.getenv("NATIVE_TTS_LIVE_VOICE"));
        props.setMaxCharsPerRequest(60); // force several chunks
        String text = "الفصل الأول. كان يا ما كان في قديم الزمان رجل حكيم يسكن قرية صغيرة.\n\nوفي صباح يوم جميل خرج الرجل إلى الحقل.";

        ChapterAudio audio = new ChapterSynthesizer(new ElevenLabsChunkTtsProvider(props), new FfmpegMediaTools(props), props)
                .synthesize(text, dir);

        int measured = new FfmpegMediaTools(props).durationMs(audio.mp3());
        System.out.println("chunks=" + new ChapterSynthesizer(null, null, props).chunksFor(text).size()
                + " measuredMs=" + measured + " lastEndMs=" + audio.endMs()[audio.endMs().length - 1]
                + " chars=" + audio.chars().length());
        assertThat(audio.durationMs()).isBetween(measured - 100, measured + 100);
        assertThat(Math.abs(audio.endMs()[audio.endMs().length - 1] - measured)).isLessThan(1200);
        assertThat(audio.chars().replaceAll("\\s+", "")).isEqualTo(text.replaceAll("\\s+", ""));
        for (int i = 1; i < audio.startMs().length; i++) {
            assertThat(audio.startMs()[i]).isGreaterThanOrEqualTo(audio.startMs()[i - 1]);
        }
    }

    @Test
    void anExtractedChapterOfARealPdfIsReadAloudWithTimingsThatMatchTheAudio(@TempDir Path dir) throws Exception {
        NativeTtsProperties props = new NativeTtsProperties();
        props.setApiKey(System.getenv("ELEVENLABS_API_KEY"));
        props.setVoiceId(System.getenv("NATIVE_TTS_LIVE_VOICE"));
        com.doova.ktab.features.extraction.dto.BookExtractionResult book = com.doova.ktab.features.extraction.ExtractionTestSupport
                .service().extract(com.doova.ktab.features.extraction.ExtractionTestSupport.fixture("book-outline.pdf"));
        com.doova.ktab.features.extraction.dto.Chapter chapter = book.chapters().get(0);
        String text = chapter.title() + "\n\n" + chapter.text();

        ChapterAudio audio = new ChapterSynthesizer(new ElevenLabsChunkTtsProvider(props), new FfmpegMediaTools(props), props)
                .synthesize(text, dir);

        int measured = new FfmpegMediaTools(props).durationMs(audio.mp3());
        int last = audio.endMs()[audio.endMs().length - 1];
        System.out.println("PDF chapter \"" + chapter.title() + "\": inputChars=" + text.length() + " audioChars=" + audio.chars().length()
                + " measuredMs=" + measured + " lastEndMs=" + last + " driftMs=" + Math.abs(measured - last));
        assertThat(Math.abs(measured - last)).isLessThan(1500);
        assertThat(audio.chars().replaceAll("\\s+", "")).isEqualTo(text.replaceAll("\\s+", ""));
    }
}
