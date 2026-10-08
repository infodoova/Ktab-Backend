package com.doova.ktab.service.book;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.genre.MainGenre;
import com.doova.ktab.model.genre.SubGenre;
import com.doova.ktab.repository.book.BookLibraryEntryRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.impl.BookSimilarityServiceImpl;
import com.doova.ktab.utils.pagination.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookSimilarityServiceImplTest {

    @Mock
    private BookLibraryEntryRepository libraryRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookResponseBuilderService responseBuilder;

    @InjectMocks
    private BookSimilarityServiceImpl similarityService;

    private MainGenre mainGenre;
    private SubGenre subGenre;

    @BeforeEach
    void setUp() {
        mainGenre = new MainGenre();
        mainGenre.setId(4L);
        mainGenre.setNameAr("سياسة وعلاقات دولية");

        subGenre = new SubGenre();
        subGenre.setId(10L);
        subGenre.setNameAr("دراسات سياسية");
        mainGenre.setSubGenres(List.of(subGenre));
    }

    @Test
    @DisplayName("getSmartSimilarBooks_returnsLibraryBooks_whenBookSourceIsLibrary")
    void getSmartSimilarBooks_returnsLibraryBooks_whenBookSourceIsLibrary() {
        Book target = new Book();
        target.setId(110L);
        target.setTitle("صمود الدبلوماسية");
        target.setBookSource(BookSource.LIBRARY);
        target.setStatus(BookStatus.PUBLISHED);
        target.setMainGenre(mainGenre);
        target.setAgeRangeMin(16);
        target.setAgeRangeMax(99);

        Book candidate = new Book();
        candidate.setId(113L);
        candidate.setTitle("قوة التفاوض");
        candidate.setBookSource(BookSource.LIBRARY);
        candidate.setStatus(BookStatus.PUBLISHED);
        candidate.setMainGenre(mainGenre);
        candidate.setAgeRangeMin(16);
        candidate.setAgeRangeMax(99);

        when(bookRepository.findById(110L)).thenReturn(Optional.of(target));
        when(bookRepository.findBroadCandidates(eq(110L), eq(4L), eq(16), eq(99)))
                .thenReturn(List.of(candidate));
        when(libraryRepository.findCollaborativeScores(110L)).thenReturn(Collections.emptyList());

        BookResponseDto candidateDto = new BookResponseDto();
        candidateDto.setId(113L);
        candidateDto.setTitle("قوة التفاوض");
        when(responseBuilder.build(candidate)).thenReturn(candidateDto);

        PageResponse<BookResponseDto> response = similarityService.getSmartSimilarBooks(110L, 0, 6);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(113L);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getSmartSimilarBooks_handlesNullAgeRanges_gracefully")
    void getSmartSimilarBooks_handlesNullAgeRanges_gracefully() {
        Book target = new Book();
        target.setId(150L);
        target.setTitle("الأجنحة المتكسرة");
        target.setBookSource(BookSource.LIBRARY);
        target.setMainGenre(mainGenre);
        target.setAgeRangeMin(null);
        target.setAgeRangeMax(null);

        Book candidate = new Book();
        candidate.setId(122L);
        candidate.setTitle("بعض الصوت");
        candidate.setBookSource(BookSource.LIBRARY);
        candidate.setMainGenre(mainGenre);
        candidate.setAgeRangeMin(16);
        candidate.setAgeRangeMax(99);

        when(bookRepository.findById(150L)).thenReturn(Optional.of(target));
        when(bookRepository.findBroadCandidates(eq(150L), eq(4L), eq(null), eq(null)))
                .thenReturn(List.of(candidate));
        when(libraryRepository.findCollaborativeScores(150L)).thenReturn(Collections.emptyList());

        BookResponseDto candidateDto = new BookResponseDto();
        candidateDto.setId(122L);
        when(responseBuilder.build(candidate)).thenReturn(candidateDto);

        PageResponse<BookResponseDto> response = similarityService.getSmartSimilarBooks(150L, 0, 6);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(122L);
    }

    @Test
    @DisplayName("getSmartSimilarBooks_handlesTargetWithoutMainGenre_returnsEmptyPage")
    void getSmartSimilarBooks_handlesTargetWithoutMainGenre_returnsEmptyPage() {
        Book target = new Book();
        target.setId(200L);
        target.setTitle("No Genre Book");
        target.setMainGenre(null);

        when(bookRepository.findById(200L)).thenReturn(Optional.of(target));

        PageResponse<BookResponseDto> response = similarityService.getSmartSimilarBooks(200L, 0, 6);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("getSmartSimilarBooks_fallsBackToPublishedBooks_whenGenreCandidatesEmpty")
    void getSmartSimilarBooks_fallsBackToPublishedBooks_whenGenreCandidatesEmpty() {
        Book target = new Book();
        target.setId(118L);
        target.setTitle("الرسائل المصرية");
        target.setBookSource(BookSource.LIBRARY);
        target.setStatus(BookStatus.PUBLISHED);
        target.setMainGenre(mainGenre);
        target.setAgeRangeMin(14);
        target.setAgeRangeMax(99);

        Book otherGenreBook = new Book();
        otherGenreBook.setId(110L);
        otherGenreBook.setTitle("صمود الدبلوماسية");
        otherGenreBook.setBookSource(BookSource.LIBRARY);
        otherGenreBook.setStatus(BookStatus.PUBLISHED);
        otherGenreBook.setMainGenre(mainGenre);
        otherGenreBook.setAgeRangeMin(16);
        otherGenreBook.setAgeRangeMax(99);

        when(bookRepository.findById(118L)).thenReturn(Optional.of(target));
        when(bookRepository.findBroadCandidates(eq(118L), eq(4L), eq(14), eq(99)))
                .thenReturn(Collections.emptyList());
        when(bookRepository.findAllByStatus(eq(BookStatus.PUBLISHED), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(otherGenreBook)));
        when(libraryRepository.findCollaborativeScores(118L)).thenReturn(Collections.emptyList());

        BookResponseDto candidateDto = new BookResponseDto();
        candidateDto.setId(110L);
        when(responseBuilder.build(otherGenreBook)).thenReturn(candidateDto);

        PageResponse<BookResponseDto> response = similarityService.getSmartSimilarBooks(118L, 0, 6);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(110L);
    }
}
