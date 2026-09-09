package com.doova.ktab.service.author;

import com.doova.ktab.dto.analytics.AuthorAnalyticsResponse;
import com.doova.ktab.dto.analytics.AuthorBookAnalyticsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuthorAnalyticsService {

    AuthorAnalyticsResponse getAuthorAnalytics(Long authorId);

    Page<AuthorBookAnalyticsResponse> getMyBooksWithAnalytics(Long authorId, Pageable pageable);
}