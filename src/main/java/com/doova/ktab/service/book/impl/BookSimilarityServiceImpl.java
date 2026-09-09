package com.doova.ktab.service.book.impl;

import com.doova.ktab.dto.book.BookScore;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.genre.MainGenre;
import com.doova.ktab.model.genre.SubGenre;
import com.doova.ktab.repository.book.BookLibraryEntryRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.BookSimilarityService;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.utils.pagination.PageResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookSimilarityServiceImpl implements BookSimilarityService {

    private final BookLibraryEntryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookResponseBuilderService responseBuilder;

    private Map<Long, Long> loadCollaborativeScores(Long bookId) {
        List<Object[]> rows = libraryRepository.findCollaborativeScores(bookId);

        return rows.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    /**
     * Computes a score based on MainGenre and shared SubGenre similarity.
     * Uses Jaccard Index for sub-genre overlap to provide a more granular score.
     */
    private double computeGenreScore(MainGenre a, MainGenre b) {
        // 1. Exact Main Genre Match
        if (a.getId().equals(b.getId())) return 1.0;

        // 2. Sub-Genre Overlap using Jaccard Index
        Set<Long> subGenresA = a.getSubGenres().stream().map(SubGenre::getId).collect(Collectors.toSet());
        Set<Long> subGenresB = b.getSubGenres().stream().map(SubGenre::getId).collect(Collectors.toSet());

        if (subGenresA.isEmpty() && subGenresB.isEmpty()) {
            return 0.6;
        }

        Set<Long> intersection = new HashSet<>(subGenresA);
        intersection.retainAll(subGenresB);

        Set<Long> union = new HashSet<>(subGenresA);
        union.addAll(subGenresB);

        if (union.isEmpty()) return 0.6;

        double jaccardIndex = (double) intersection.size() / union.size();
        return 0.6 + (jaccardIndex * 0.4);
    }

    private double computeAgeOverlap(Book target, Book candidate) {
        int overlap = Math.min(target.getAgeRangeMax(), candidate.getAgeRangeMax())
                - Math.max(target.getAgeRangeMin(), candidate.getAgeRangeMin());

        if (overlap <= 0) return 0.0;

        int range = Math.max(target.getAgeRangeMax(), candidate.getAgeRangeMax())
                - Math.min(target.getAgeRangeMin(), candidate.getAgeRangeMin());

        return (double) overlap / range;
    }

    private double computeSimilarity(Book target, Book candidate, Map<Long, Long> collabMap, Long maxCollabCount) {
        double genreScore = computeGenreScore(target.getMainGenre(), candidate.getMainGenre());
        double ageScore = computeAgeOverlap(target, candidate);

        long collabRaw = collabMap.getOrDefault(candidate.getId(), 0L);
        double collabScore = (double) collabRaw / maxCollabCount;

        // Hybrid Weighted Sum (0.5 + 0.3 + 0.2 = 1.0)
        return (genreScore * 0.5) + (ageScore * 0.3) + (collabScore * 0.2);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getSmartSimilarBooks(Long bookId, int page, int size) {
        Book target = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book with ID " + bookId + " not found"));

        List<Book> candidates = bookRepository.findBroadCandidates(
                bookId,
                target.getMainGenre().getId(),
                target.getAgeRangeMin(),
                target.getAgeRangeMax());

        Map<Long, Long> collabMap = loadCollaborativeScores(bookId);
        Long maxCollabCount = collabMap.values().stream().max(Long::compareTo).orElse(1L);

        List<BookScore> scored = candidates.stream()
                .filter(b -> !b.getId().equals(bookId))
                .map(b -> new BookScore(b, computeSimilarity(target, b, collabMap, maxCollabCount)))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .toList();

        int totalElements = scored.size();

        int start = page * size;
        if (start >= totalElements) {
            return PageResponse.empty(page, size);
        }

        int end = Math.min(start + size, totalElements);
        List<Book> pageResults = scored.subList(start, end).stream().map(BookScore::book).toList();

        int totalPages = (int) Math.ceil((double) totalElements / size);
        boolean isLast = end == totalElements;

        return new PageResponse<>(
                pageResults.stream().map(responseBuilder::build).toList(),
                page,
                size,
                totalElements,
                totalPages,
                isLast
        );
    }
}
