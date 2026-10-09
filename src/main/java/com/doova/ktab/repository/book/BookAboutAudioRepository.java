package com.doova.ktab.repository.book;

import com.doova.ktab.model.book.BookAboutAudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookAboutAudioRepository extends JpaRepository<BookAboutAudio, Long> {

    /** The audios of the given books, in one query. */
    List<BookAboutAudio> findAllByBookIdIn(Collection<Long> bookIds);
}
