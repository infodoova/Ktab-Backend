package com.doova.ktab.features.nativetts.batch;

import com.doova.ktab.features.nativetts.ChapterAudio;
import com.doova.ktab.features.nativetts.ChapterSynthesizer;
import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import com.doova.ktab.features.studio.model.BookAudioChapter;
import com.doova.ktab.features.studio.repository.BookAudioChapterRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class NativeAudiobookTaskletsTest {

    private final BookRepository books = mock(BookRepository.class);
    private final BookSectionRepository sections = mock(BookSectionRepository.class);
    private final BookPageRepository pages = mock(BookPageRepository.class);
    private final BookAudioChapterRepository audio = mock(BookAudioChapterRepository.class);
    private final ChapterSynthesizer synthesizer = mock(ChapterSynthesizer.class);
    private final S3Client s3 = mock(S3Client.class);
    private final NativeTtsProperties props = new NativeTtsProperties();
    private final Book book = new Book();
    private final List<BookAudioChapter> savedAudio = new ArrayList<>();
    private Path fakeMp3;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        props.setVoiceId("voice-1");
        book.setHasAudio(false);
        book.setTitle("كتاب");
        fakeMp3 = tmp.resolve("chapter.mp3");
        Files.write(fakeMp3, new byte[]{1, 2, 3, 4});
        when(books.findById(7L)).thenReturn(Optional.of(book));
        when(audio.save(any(BookAudioChapter.class))).thenAnswer(i -> {
            savedAudio.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(audio.findByBook_IdAndSortOrder(anyLong(), any())).thenReturn(Optional.empty());
        when(sections.save(any(BookSection.class))).thenAnswer(i -> i.getArgument(0));
        when(synthesizer.synthesize(anyString(), any())).thenReturn(
                new ChapterAudio(fakeMp3, 5000, "ab", new int[]{0, 100}, new int[]{100, 200}));
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    private NativeSynthesizeTasklet tasklet() {
        return new NativeSynthesizeTasklet(7L, props, synthesizer, books, sections, pages, audio, s3, "bucket",
                new ObjectMapper(), new SimpleMeterRegistry());
    }

    private static BookSection section(long id, String title, int level, BookSection parent, int sortOrder, int start, int end) {
        BookSection s = new BookSection();
        ReflectionTestUtils.setField(s, "id", id);
        s.setTitle(title);
        s.setLevel((short) level);
        s.setParent(parent);
        s.setSortOrder(sortOrder);
        s.setStartPage(start);
        s.setEndPage(end);
        return s;
    }

    private static BookPage page(int n, BookSection section) {
        BookPage p = new BookPage();
        p.setPageNumber(n);
        p.setSection(section);
        p.setMarkdownClean("page-" + n + " نص الصفحة");
        return p;
    }

    private void givenBook(List<BookSection> allSections, List<BookPage> bookPages) {
        when(sections.findByBook_IdOrderBySortOrderAsc(7L)).thenReturn(allSections);
        when(pages.findByBookIdOrderByPageNumberAsc(7L)).thenReturn(bookPages);
    }

    @Test
    void oneAudioChapterPerTopLevelSectionAtTheStudioKeys() throws Exception {
        BookSection intro = section(1, "المقدمة", 1, null, 0, 4, 5);
        BookSection ch1 = section(2, "الفصل الأول", 1, null, 1, 6, 9);
        givenBook(List.of(intro, ch1), List.of(page(4, intro), page(5, intro), page(6, ch1), page(7, ch1)));

        tasklet().execute(null, null);

        ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3, times(4)).putObject(puts.capture(), any(RequestBody.class));
        assertThat(puts.getAllValues()).extracting(PutObjectRequest::key).containsExactlyInAnyOrder(
                "audio/7/chapters/ch-0001.mp3", "audio/7/timings/ch-0001.json.gz",
                "audio/7/chapters/ch-0002.mp3", "audio/7/timings/ch-0002.json.gz");
        assertThat(puts.getAllValues()).filteredOn(r -> r.key().endsWith(".json.gz"))
                .allSatisfy(r -> assertThat(r.contentEncoding()).isEqualTo("gzip"));
        assertThat(savedAudio).extracting(BookAudioChapter::getSortOrder).containsExactly(1, 2);
        assertThat(savedAudio.get(1).getBookSection().getTitle()).isEqualTo("الفصل الأول");
        assertThat(savedAudio.get(0).getAudioPath()).isEqualTo("audio/7/chapters/ch-0001.mp3");
        assertThat(savedAudio.get(0).getTimingsPath()).isEqualTo("audio/7/timings/ch-0001.json.gz");
        assertThat(savedAudio.get(0).getDurationMs()).isEqualTo(5000);
        assertThat(savedAudio.get(0).getSizeBytes()).isEqualTo(4);
        assertThat(savedAudio.get(0).getSha256()).hasSize(64);
        assertThat(book.getHasAudio()).isTrue();
    }

    @Test
    void theChapterTextStartsWithItsTitleAndIncludesSubsectionPages() throws Exception {
        BookSection ch1 = section(2, "الفصل الأول", 1, null, 0, 6, 9);
        BookSection sub = section(3, "المبحث الأول", 2, ch1, 1, 7, 7);
        givenBook(List.of(ch1, sub), List.of(page(6, ch1), page(7, sub), page(9, ch1)));

        tasklet().execute(null, null);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(synthesizer, times(1)).synthesize(text.capture(), any());
        assertThat(text.getValue()).startsWith("الفصل الأول").contains("page-6").contains("page-7").contains("page-9");
        assertThat(text.getValue().indexOf("page-6")).isLessThan(text.getValue().indexOf("page-7"));
    }

    @Test
    void realDatabaseIdsAreComparedByValueNotByReference() throws Exception {
        BookSection ch1 = section(100_001L, "الفصل الأول", 1, null, 0, 6, 9);
        // Hibernate can return a different object with the same id (a proxy); the ids must be compared by value
        BookSection sameChapterAsAnotherObject = section(100_001L, "الفصل الأول", 1, null, 0, 6, 9);
        BookSection sub = section(100_002L, "المبحث الأول", 2, sameChapterAsAnotherObject, 1, 7, 7);
        givenBook(List.of(ch1, sub), List.of(page(6, ch1), page(7, sub)));

        tasklet().execute(null, null);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(synthesizer).synthesize(text.capture(), any());
        assertThat(text.getValue()).contains("page-6").contains("page-7");
    }

    @Test
    void pagesOutsideEverySectionAreNotRead() throws Exception {
        BookSection ch1 = section(2, "الفصل الأول", 1, null, 0, 6, 9);
        givenBook(List.of(ch1), List.of(page(3, null), page(6, ch1)));

        tasklet().execute(null, null);

        verify(synthesizer).synthesize(argThatNotContaining("page-3"), any());
    }

    private static String argThatNotContaining(String s) {
        return org.mockito.ArgumentMatchers.argThat(t -> !t.contains(s));
    }

    @Test
    void timingsUseTheStudioColumnarFormat() throws Exception {
        BookSection ch1 = section(2, "الفصل الأول", 1, null, 0, 6, 9);

        byte[] gz = NativeSynthesizeTasklet.gzippedTimings(new ObjectMapper(), "native-2",
                new ChapterAudio(fakeMp3, 5000, "ab", new int[]{0, 100}, new int[]{100, 200}));

        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            JsonNode j = new ObjectMapper().readTree(in.readAllBytes());
            assertThat(j.get("v").asInt()).isEqualTo(1);
            assertThat(j.get("chapterId").asText()).isEqualTo("native-2");
            assertThat(j.get("chars").asText()).isEqualTo("ab");
            assertThat(j.get("startMs")).hasSize(2);
            assertThat(j.get("endMs").get(1).asInt()).isEqualTo(200);
        }
    }

    @Test
    void resumeSkipsChaptersThatAlreadyHaveAudioInStorage() throws Exception {
        BookSection intro = section(1, "المقدمة", 1, null, 0, 4, 5);
        BookSection ch1 = section(2, "الفصل الأول", 1, null, 1, 6, 9);
        givenBook(List.of(intro, ch1), List.of(page(4, intro), page(6, ch1)));
        BookAudioChapter existing = new BookAudioChapter();
        existing.setAudioPath("audio/7/chapters/ch-0001.mp3");
        when(audio.findByBook_IdAndSortOrder(7L, 1)).thenReturn(Optional.of(existing));

        tasklet().execute(null, null);

        verify(synthesizer, times(1)).synthesize(contains("الفصل الأول"), any());
        verify(synthesizer, never()).synthesize(contains("المقدمة"), any());
        assertThat(book.getHasAudio()).isTrue();
    }

    @Test
    void aRowWhoseFileIsGoneFromStorageIsSynthesizedAgain() throws Exception {
        BookSection intro = section(1, "المقدمة", 1, null, 0, 4, 5);
        givenBook(List.of(intro), List.of(page(4, intro)));
        BookAudioChapter existing = new BookAudioChapter();
        existing.setAudioPath("audio/7/chapters/ch-0001.mp3");
        when(audio.findByBook_IdAndSortOrder(7L, 1)).thenReturn(Optional.of(existing));
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        tasklet().execute(null, null);

        verify(synthesizer, times(1)).synthesize(anyString(), any());
    }

    @Test
    void aBookWithNoSectionsBecomesOneChapterOverEveryPage() throws Exception {
        givenBook(List.of(), List.of(page(1, null), page(2, null)));
        book.setPageCount(2);

        tasklet().execute(null, null);

        verify(sections).save(any(BookSection.class));
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(synthesizer).synthesize(text.capture(), any());
        assertThat(text.getValue()).contains("page-1").contains("page-2");
        assertThat(savedAudio).singleElement().satisfies(c -> assertThat(c.getBookSection().getTitle()).isEqualTo("كتاب"));
    }

    @Test
    void hasAudioStaysFalseIfAnyChapterFailsButFinishedOnesAreKept() throws Exception {
        BookSection intro = section(1, "المقدمة", 1, null, 0, 4, 5);
        BookSection ch1 = section(2, "الفصل الأول", 1, null, 1, 6, 9);
        givenBook(List.of(intro, ch1), List.of(page(4, intro), page(6, ch1)));
        when(synthesizer.synthesize(contains("الفصل الأول"), any())).thenThrow(new IllegalStateException("tts down"));

        assertThatThrownBy(() -> tasklet().execute(null, null)).hasMessageContaining("tts down");

        assertThat(book.getHasAudio()).isFalse();
        assertThat(savedAudio).extracting(BookAudioChapter::getSortOrder).containsExactly(1);
    }

    @Test
    void refusesToStartWithoutANarratorVoice() {
        props.setVoiceId(" ");
        assertThatThrownBy(() -> tasklet().execute(null, null)).hasMessageContaining("KTAB_NATIVE_TTS_VOICE_ID");
        verifyNoInteractions(synthesizer);
    }

    // ---- estimate ----

    @Test
    void estimateFailsWithoutAVoiceAndAboveTheCharacterCap() {
        when(pages.getTotalCharacterCount(7L)).thenReturn(1_000L);
        NativeEstimateTasklet estimate = new NativeEstimateTasklet(7L, props, pages);
        assertThat(estimate.estimate()).isEqualTo(1_000L);

        props.setMaxCharsPerBook(500);
        assertThatThrownBy(estimate::estimate).hasMessageContaining("1000").hasMessageContaining("500");

        props.setMaxCharsPerBook(1_000_000);
        props.setVoiceId("");
        assertThatThrownBy(estimate::estimate).hasMessageContaining("KTAB_NATIVE_TTS_VOICE_ID");
    }

    @Test
    void estimateFailsForABookWithNoText() {
        when(pages.getTotalCharacterCount(7L)).thenReturn(0L);
        assertThatThrownBy(() -> new NativeEstimateTasklet(7L, props, pages).estimate()).hasMessageContaining("no text");
    }
}
