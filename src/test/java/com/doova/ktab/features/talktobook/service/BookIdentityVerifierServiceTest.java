package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.features.talktobook.service.impl.BookIdentityVerifierServiceImpl;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookIdentityVerifierServiceTest {

    @Mock
    private BookSectionRepository bookSectionRepository;

    private BookIdentityVerifierServiceImpl verifierService;

    @BeforeEach
    void setUp() {
        verifierService = new BookIdentityVerifierServiceImpl(bookSectionRepository);
    }

    @Test
    @DisplayName("verifyBookIdentity_titleAndAuthorMatch_returnsTrue")
    void verifyBookIdentity_titleAndAuthorMatch_returnsTrue() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("The Alchemist");
        book.setCustomAuthorName("Paulo Coelho");

        String webContent = "The Alchemist is a novel by Brazilian author Paulo Coelho that follows Santiago...";

        boolean verified = verifierService.verifyBookIdentity(book, webContent);

        assertThat(verified).isTrue();
    }

    @Test
    @DisplayName("verifyBookIdentity_titleMissing_returnsFalse")
    void verifyBookIdentity_titleMissing_returnsFalse() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("The Alchemist");
        book.setCustomAuthorName("Paulo Coelho");

        String webContent = "This is a book about an unnamed traveler traversing the desert.";

        boolean verified = verifierService.verifyBookIdentity(book, webContent);

        assertThat(verified).isFalse();
    }

    @Test
    @DisplayName("verifyBookIdentity_titlePresentAndSectionMatches_returnsTrue")
    void verifyBookIdentity_titlePresentAndSectionMatches_returnsTrue() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("Quantum Mechanics Fundamentals");

        BookSection section = new BookSection();
        section.setTitle("Schrodinger Equation");

        when(bookSectionRepository.findByBook_IdOrderBySortOrderAsc(1L)).thenReturn(List.of(section));

        String webContent = "Quantum Mechanics Fundamentals discusses the Schrodinger Equation in chapter 3.";

        boolean verified = verifierService.verifyBookIdentity(book, webContent);

        assertThat(verified).isTrue();
    }
}
