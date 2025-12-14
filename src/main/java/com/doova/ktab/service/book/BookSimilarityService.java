package com.doova.ktab.service.book;

import com.doova.ktab.api.dto.BookScore;
import com.doova.ktab.api.dto.response.BookResponseDto;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.configuration.MainGenre;
import com.doova.ktab.repository.book.BookLibraryEntryRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.helpers.BookResponseBuilderService;
import com.doova.ktab.utils.PageResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookSimilarityService {

    private BookLibraryEntryRepository libraryRepository;
    private BookRepository bookRepository;
    private final BookResponseBuilderService responseBuilder;

    private Map<Long, Integer> loadCollaborativeScores(Long bookId) {
        Map<Long, Integer> map = new HashMap<>();
        List<Object[]> rows = libraryRepository.findCollaborativeScores(bookId);

        for (Object[] row : rows) {
            Long id = (Long) row[0];
            Integer count = ((Long) row[1]).intValue(); // SQL returns Long
            map.put(id, count);
        }
        return map;
    }

    private double computeGenreScore(MainGenre a, MainGenre b) {
        if (a.getId().equals(b.getId())) return 1.0;   // exact match
        return 0.6; // fallback for same cluster → can be enhanced later
    }

    private double computeAgeOverlap(Book t, Book c) {

        int overlap = Math.min(t.getAgeRangeMax(), c.getAgeRangeMax()) - Math.max(t.getAgeRangeMin(), c.getAgeRangeMin());

        if (overlap <= 0) return 0; // no overlap

        int range = Math.max(t.getAgeRangeMax(), c.getAgeRangeMax()) - Math.min(t.getAgeRangeMin(), c.getAgeRangeMin());

        return (double) overlap / range;
    }

    private double computeSimilarity(Book target, Book candidate, Map<Long, Integer> collabMap, int maxCollab) {
        double genreScore = computeGenreScore(target.getMainGenre(), candidate.getMainGenre());
        double ageScore = computeAgeOverlap(target, candidate);

        double collabRaw = collabMap.getOrDefault(candidate.getId(), 0);
        double collabScore = (double) collabRaw / maxCollab;

        return (genreScore * 0.5) + (ageScore * 0.3) + (collabScore * 0.2);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getSmartSimilarBooks(Long bookId, int page, int size) {

        Book target = bookRepository.findById(bookId).orElseThrow(() -> new EntityNotFoundException("Book not found"));

        // 1. Find initial candidate pool
        List<Book> candidates = bookRepository.findBroadCandidates(bookId, target.getMainGenre().getId(), target.getAgeRangeMin(), target.getAgeRangeMax());

        // 2. Collaborative filtering data
        Map<Long, Integer> collabMap = loadCollaborativeScores(bookId);
        int maxCollab = collabMap.values().stream().max(Integer::compareTo).orElse(1);

        // 3. Score each book
        List<BookScore> scored = candidates.stream().map(b -> new BookScore(b, computeSimilarity(target, b, collabMap, maxCollab))).sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // DESC
                .toList();

        // 4. Manual pagination
        int start = page * size;
        int end = Math.min(start + size, scored.size());
        List<Book> pageResults = scored.subList(start, end).stream().map(BookScore::book).toList();

        return new PageResponse<>(pageResults.stream().map(responseBuilder::build).toList(), page, size, scored.size(), (int) Math.ceil((double) scored.size() / size), end == scored.size());
    }


}
