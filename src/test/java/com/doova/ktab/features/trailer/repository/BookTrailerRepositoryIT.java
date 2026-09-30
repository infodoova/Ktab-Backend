package com.doova.ktab.features.trailer.repository;

import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.support.TrailerFixtures;
import com.doova.ktab.model.book.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTrailerRepositoryIT extends StorybookJpaIT {

    @Autowired BookTrailerRepository trailers;

    private Book book() {
        return TrailerFixtures.publishedBook(em, UserFixtures.reader(em, "a-" + System.nanoTime() + "@x.com"));
    }

    private BookTrailer trailer(Book book, TrailerStatus status) {
        BookTrailer t = new BookTrailer();
        t.setBookId(book.getId());
        t.setStatus(status);
        return t;
    }

    @Test
    void onlyOneActiveTrailerPerBook() {
        Book book = book();
        trailers.saveAndFlush(trailer(book, TrailerStatus.RUNNING));
        trailers.saveAndFlush(trailer(book, TrailerStatus.READY));

        assertThatThrownBy(() -> trailers.saveAndFlush(trailer(book, TrailerStatus.QUEUED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsBySessionIdAndDueRunningTrailers() {
        BookTrailer due = trailer(book(), TrailerStatus.RUNNING);
        due.setSessionId("sesn_due");
        due.setNextCheckAt(Instant.now().minusSeconds(5));
        trailers.saveAndFlush(due);
        BookTrailer later = trailer(book(), TrailerStatus.RUNNING);
        later.setSessionId("sesn_later");
        later.setNextCheckAt(Instant.now().plusSeconds(600));
        trailers.saveAndFlush(later);

        assertThat(trailers.findBySessionId("sesn_due")).isPresent();
        assertThat(trailers.findDueRunning(Instant.now(), PageRequest.of(0, 50)))
                .extracting(BookTrailer::getSessionId).contains("sesn_due").doesNotContain("sesn_later");
    }
}
