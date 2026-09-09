package com.doova.ktab.service.author.impl;

import com.doova.ktab.dto.analytics.AuthorAnalyticsResponse;
import com.doova.ktab.dto.analytics.AuthorBookAnalyticsResponse;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.repository.book.BookLibraryEntryRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.author.AuthorAnalyticsService;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthorAnalyticsServiceImpl implements AuthorAnalyticsService {

    private final BookRepository bookRepository;
    private final BookLibraryEntryRepository bookLibraryEntryRepository;
    private final FileStorageService fileStorageService;

    @Override
    public AuthorAnalyticsResponse getAuthorAnalytics(Long authorId) {
        long totalBooks = bookRepository.countByAuthor_Id(authorId);
        long totalReads = bookLibraryEntryRepository.countAuthorTotalReads(authorId);
        long totalReviews = bookRepository.sumAuthorTotalReviews(authorId);
        BigDecimal averageRating = bookRepository.findAuthorAverageRating(authorId);

        return new AuthorAnalyticsResponse(totalBooks, totalReads, averageRating, totalReviews);
    }

    @Override
    public Page<AuthorBookAnalyticsResponse> getMyBooksWithAnalytics(Long authorId, Pageable pageable) {
        return bookRepository.findAuthorBooksWithAnalytics(authorId, pageable).map(r -> new AuthorBookAnalyticsResponse(r.bookId(), r.title(), r.status(), r.publishDate(), r.averageRating(), r.totalReviews(), r.mainGenre(), r.totalReaders(), fileStorageService.getFileUrl(r.coverImageUrl(), UrlStrategy.SIGNED)));
    }
}
