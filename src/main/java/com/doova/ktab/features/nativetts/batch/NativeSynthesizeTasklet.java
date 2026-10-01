package com.doova.ktab.features.nativetts.batch;

import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.features.nativetts.ChapterAudio;
import com.doova.ktab.features.nativetts.ChapterSynthesizer;
import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.features.studio.batch.TimingIndexTasklet;
import com.doova.ktab.features.studio.model.BookAudioChapter;
import com.doova.ktab.features.studio.repository.BookAudioChapterRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

/**
 * Reads every top-level section aloud with Ktab's own TTS and stores exactly what Studio's audiobook job stores:
 * the MP3 and gzipped timing index at the same keys, plus one tbl_book_audio_chapters row per chapter. A finished
 * chapter is committed in its own transaction, so a re-run after a failure never pays for it again.
 */
@Slf4j
public class NativeSynthesizeTasklet implements Tasklet {

    private static final String AUDIO_KEY_FMT = "audio/%d/chapters/ch-%04d.mp3";
    private static final String TIMING_KEY_FMT = "audio/%d/timings/ch-%04d.json.gz";

    private final Long bookId;
    private final NativeTtsProperties props;
    private final ChapterSynthesizer synthesizer;
    private final BookRepository bookRepository;
    private final BookSectionRepository sectionRepository;
    private final BookPageRepository pageRepository;
    private final BookAudioChapterRepository audioRepository;
    private final S3Client s3;
    private final String bucket;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final Consumer<Runnable> ownTransaction;

    public NativeSynthesizeTasklet(Long bookId, NativeTtsProperties props, ChapterSynthesizer synthesizer,
                                   BookRepository bookRepository, BookSectionRepository sectionRepository,
                                   BookPageRepository pageRepository, BookAudioChapterRepository audioRepository,
                                   S3Client s3, String bucket, ObjectMapper json, MeterRegistry meters) {
        this(bookId, props, synthesizer, bookRepository, sectionRepository, pageRepository, audioRepository, s3, bucket,
                json, meters, Runnable::run);
    }

    /** {@code ownTransaction} runs a chapter's database writes in a transaction of their own (REQUIRES_NEW). */
    public NativeSynthesizeTasklet(Long bookId, NativeTtsProperties props, ChapterSynthesizer synthesizer,
                                   BookRepository bookRepository, BookSectionRepository sectionRepository,
                                   BookPageRepository pageRepository, BookAudioChapterRepository audioRepository,
                                   S3Client s3, String bucket, ObjectMapper json, MeterRegistry meters,
                                   Consumer<Runnable> ownTransaction) {
        this.bookId = bookId;
        this.props = props;
        this.synthesizer = synthesizer;
        this.bookRepository = bookRepository;
        this.sectionRepository = sectionRepository;
        this.pageRepository = pageRepository;
        this.audioRepository = audioRepository;
        this.s3 = s3;
        this.bucket = bucket;
        this.json = json;
        this.meters = meters;
        this.ownTransaction = ownTransaction;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (props.getVoiceId() == null || props.getVoiceId().isBlank()) {
            throw new IllegalStateException("No narrator voice is configured: set KTAB_NATIVE_TTS_VOICE_ID");
        }
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));
        List<BookPage> pages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);

        List<BookSection> chapters = new ArrayList<>(sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId).stream()
                .filter(s -> s.getParent() == null).toList());
        boolean wholeBook = chapters.isEmpty();
        if (wholeBook) {
            chapters.add(wholeBookSection(book, pages.size()));
        }

        int done = 0;
        for (int i = 0; i < chapters.size(); i++) {
            BookSection chapter = chapters.get(i);
            int sortOrder = i + 1;
            if (alreadyDone(sortOrder)) {
                meters.counter("audiobook.native.chapters", "outcome", "skipped").increment();
                done++;
                continue;
            }
            String text = chapterText(chapter, pages, wholeBook);
            if (text == null) {
                log.warn("native audiobook bookId={} chapter {} \"{}\" has no text; skipped", bookId, sortOrder, chapter.getTitle());
                continue;
            }
            synthesizeAndStore(book, chapter, sortOrder, text);
            meters.counter("audiobook.native.chapters", "outcome", "done").increment();
            meters.counter("audiobook.native.chars").increment(text.length());
            done++;
        }
        if (done == 0) {
            throw new IllegalStateException("No chapter of the book had any text to read");
        }
        book.setHasAudio(true);
        bookRepository.save(book);
        log.info("native audiobook finished bookId={} chapters={}", bookId, done);
        return RepeatStatus.FINISHED;
    }

    private boolean alreadyDone(int sortOrder) {
        Optional<BookAudioChapter> existing = audioRepository.findByBook_IdAndSortOrder(bookId, sortOrder);
        return existing.isPresent() && objectExists(existing.get().getAudioPath());
    }

    private boolean objectExists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /** The chapter's title, then the clean text of every page that belongs to it or to one of its subsections. */
    private static String chapterText(BookSection chapter, List<BookPage> pages, boolean wholeBook) {
        StringBuilder body = new StringBuilder();
        for (BookPage p : pages) {
            boolean belongs = wholeBook || (p.getSection() != null && java.util.Objects.equals(rootOf(p.getSection()), rootOf(chapter)));
            if (!belongs) {
                continue;
            }
            String t = p.getMarkdownClean() != null ? p.getMarkdownClean() : p.getMarkdownContent();
            if (t != null && !t.isBlank()) {
                if (body.length() > 0) {
                    body.append("\n\n");
                }
                body.append(t.strip());
            }
        }
        return body.length() == 0 ? null : chapter.getTitle() + "\n\n" + body;
    }

    private static Object rootOf(BookSection s) {
        BookSection cur = s;
        while (cur.getParent() != null) {
            cur = cur.getParent();
        }
        return cur.getId();
    }

    private BookSection wholeBookSection(Book book, int pageCount) {
        BookSection s = new BookSection();
        s.setBook(book);
        String title = book.getTitle() == null || book.getTitle().isBlank() ? "الكتاب" : book.getTitle();
        s.setTitle(title);
        s.setTitleNormalized(ArabicTextNormalizer.normalize(title));
        s.setLevel((short) 1);
        s.setSortOrder(0);
        s.setStartPage(1);
        s.setEndPage(Math.max(1, pageCount));
        s.setSectionType(SectionType.CHAPTER);
        s.setSource(StructureSource.TEXT_LAYER);
        s.setConfidence(BigDecimal.ZERO);
        s.setNeedsReview(true);
        return sectionRepository.save(s);
    }

    private void synthesizeAndStore(Book book, BookSection chapter, int sortOrder, String text) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("native-tts-" + bookId + "-");
            ChapterAudio audio = synthesizer.synthesize(text, workDir);
            byte[] mp3 = Files.readAllBytes(audio.mp3());
            String sha = sha256(mp3);
            String audioKey = String.format(AUDIO_KEY_FMT, bookId, sortOrder);
            String timingsKey = String.format(TIMING_KEY_FMT, bookId, sortOrder);
            byte[] timings = gzippedTimings(json, "native-" + chapter.getId(), audio);

            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(audioKey).contentType("audio/mpeg")
                    .contentLength((long) mp3.length).build(), RequestBody.fromBytes(mp3));
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(timingsKey).contentType("application/gzip")
                    .contentEncoding("gzip").contentLength((long) timings.length).build(), RequestBody.fromBytes(timings));

            ownTransaction.accept(() -> {
                BookAudioChapter row = audioRepository.findByBook_IdAndSortOrder(bookId, sortOrder).orElseGet(BookAudioChapter::new);
                row.setBook(book);
                row.setBookSection(chapter);
                row.setSortOrder(sortOrder);
                row.setAudioPath(audioKey);
                row.setTimingsPath(timingsKey);
                row.setDurationMs(audio.durationMs());
                row.setSizeBytes(mp3.length);
                row.setSha256(sha);
                audioRepository.save(row);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    /** The Studio columnar timing index: {"v":1,"chapterId":...,"chars":...,"startMs":[...],"endMs":[...]}, gzipped. */
    static byte[] gzippedTimings(ObjectMapper json, String chapterId, ChapterAudio audio) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                gz.write(json.writeValueAsBytes(new TimingIndexTasklet.ColumnarTimingPayload(
                        1, chapterId, audio.chars(), audio.startMs(), audio.endMs())));
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // a leftover temp directory is harmless
        }
    }
}
