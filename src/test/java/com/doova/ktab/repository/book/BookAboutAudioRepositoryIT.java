package com.doova.ktab.repository.book;

import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.model.book.BookAboutAudio;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The about-the-book audio table on a real Postgres built by Ktab's full Flyway history, so migration V37 and the entity
 * mapping are checked against each other (schema validation is on), and the rules the table is meant to enforce.
 */
class BookAboutAudioRepositoryIT extends StorybookJpaIT {

    @Autowired
    private BookAboutAudioRepository repository;

    @Autowired
    private EntityManager entityManager;

    /** A bare book row: the title is the only column without a default. */
    private long newBook(String title) {
        return ((Number) entityManager
                .createNativeQuery("insert into tbl_books (col_title) values (:title) returning col_id")
                .setParameter("title", title)
                .getSingleResult()).longValue();
    }

    private static BookAboutAudio audio(long bookId, String path, String description) {
        BookAboutAudio a = new BookAboutAudio();
        a.setBookId(bookId);
        a.setStoragePath(path);
        a.setFileName("intro.mp3");
        a.setMimeType("audio/mpeg");
        a.setFileSize(47_691);
        a.setDurationSeconds(40);
        a.setDescription(description);
        return a;
    }

    @Test
    void anAudioWithItsPathAndArabicDescriptionRoundTrips() {
        long book = newBook("Book");
        repository.saveAndFlush(audio(book, "books/1/about-audio/a.mp3", "كتاب عن الدبلوماسية الأمريكية في عشرين عامًا."));
        entityManager.clear();

        BookAboutAudio loaded = repository.findById(book).orElseThrow();

        assertThat(loaded.getStoragePath()).isEqualTo("books/1/about-audio/a.mp3");
        assertThat(loaded.getDescription()).isEqualTo("كتاب عن الدبلوماسية الأمريكية في عشرين عامًا.");
        assertThat(loaded.getFileSize()).isEqualTo(47_691);
        assertThat(loaded.getDurationSeconds()).isEqualTo(40);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void aBookHasAtMostOneAudioAndSavingAgainReplacesIt() {
        long book = newBook("Book");
        repository.saveAndFlush(audio(book, "books/1/about-audio/old.mp3", "old"));
        entityManager.clear();

        BookAboutAudio again = repository.findById(book).orElseThrow();
        again.setStoragePath("books/1/about-audio/new.mp3");
        again.setDescription("new");
        repository.saveAndFlush(again);
        entityManager.clear();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(book).orElseThrow().getStoragePath()).isEqualTo("books/1/about-audio/new.mp3");
    }

    @Test
    void twoRowsForTheSameBookAreRefusedByTheDatabase() {
        long book = newBook("Book");
        repository.saveAndFlush(audio(book, "p/a.mp3", null));

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                        "insert into tbl_book_about_audios (col_book_id, col_storage_path, col_file_name, col_mime_type, col_file_size) "
                                + "values (:id, 'p/b.mp3', 'b.mp3', 'audio/mpeg', 1)")
                .setParameter("id", book).executeUpdate())
                .hasMessageContaining("tbl_book_about_audios");
    }

    @Test
    void anAudioForABookThatDoesNotExistIsRefused() {
        assertThatThrownBy(() -> repository.saveAndFlush(audio(987_654_321L, "p/a.mp3", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingABookDeletesItsAudioRow() {
        long book = newBook("Book");
        repository.saveAndFlush(audio(book, "p/a.mp3", null));

        entityManager.createNativeQuery("delete from tbl_books where col_id = :id").setParameter("id", book).executeUpdate();
        entityManager.clear();

        assertThat(repository.findById(book)).isEmpty();
    }

    @Test
    void severalBooksAreLookedUpInOneQueryAndOnlyTheRequestedOnesComeBack() {
        long a = newBook("A");
        long b = newBook("B");
        long c = newBook("C");
        repository.saveAndFlush(audio(a, "p/a.mp3", "a"));
        repository.saveAndFlush(audio(b, "p/b.mp3", "b"));
        repository.saveAndFlush(audio(c, "p/c.mp3", "c"));
        entityManager.clear();

        List<BookAboutAudio> found = repository.findAllByBookIdIn(List.of(a, c, 987_654_321L));

        assertThat(found).extracting(BookAboutAudio::getBookId).containsExactlyInAnyOrder(a, c);
    }
}
