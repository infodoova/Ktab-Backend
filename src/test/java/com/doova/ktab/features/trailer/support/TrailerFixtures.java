package com.doova.ktab.features.trailer.support;

import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

public final class TrailerFixtures {

    private TrailerFixtures() {
    }

    public static Book publishedBook(TestEntityManager em, User author) {
        Book book = new Book();
        book.setTitle("ثورة دونالد ترامب");
        book.setDescription("قراءة ألكسندر دوغين لعودة ترامب.");
        book.setLanguage("ar");
        book.setAuthor(author);
        book.setStatus(BookStatus.PUBLISHED);
        return em.persistAndFlush(book);
    }
}
