package com.doova.ktab.features.talktobook.service.impl;

import com.doova.ktab.features.talktobook.config.TalkToBookProperties;
import com.doova.ktab.features.talktobook.service.BookWebSearchService;
import com.doova.ktab.model.book.Book;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class BookWebSearchServiceImpl implements BookWebSearchService {

    private final TalkToBookProperties properties;
    private final RestClient restClient;

    public BookWebSearchServiceImpl(TalkToBookProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;

        // Senior backend rule: Explicit connect and read timeouts on every HTTP client
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());

        this.restClient = restClientBuilder
                .requestFactory(factory)
                .build();
    }

    @Override
    public Optional<String> searchBookOverview(Book book) {
        if (!properties.isWebSearchEnabled()) {
            log.debug("Web search is disabled via configuration");
            return Optional.empty();
        }

        String author = book.getCustomAuthorName() != null ? book.getCustomAuthorName() :
                (book.getAuthor() != null ? book.getAuthor().getFirstName() + " " + book.getAuthor().getLastName() : "");

        String cleanTitle = book.getTitle().replace(":", " ").replace("-", " ").replaceAll("\\s+", " ").trim();
        String query = (author != null && !author.isBlank())
                ? String.format("كتاب %s %s ملخص", cleanTitle, author).trim()
                : String.format("كتاب %s ملخص", cleanTitle).trim();

        try {
            log.info("Executing external web search for book: '{}'", book.getTitle());

            // DuckDuckGo Lite / HTML search query or generic search proxy
            String url = properties.getSearchBaseUrl() + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            String responseBody = restClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (compatible; KtabAgent/1.0)")
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            // Clean basic HTML markup to extract plain text snippets
            String cleanedText = responseBody
                    .replaceAll("<script[^>]*>[\\s\\S]*?</script>", " ")
                    .replaceAll("<style[^>]*>[\\s\\S]*?</style>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&quot;", "\"")
                    .replaceAll("\\s+", " ")
                    .trim();

            // Truncate to a reasonable context window length (~1,200 chars)
            if (cleanedText.length() > 1500) {
                cleanedText = cleanedText.substring(0, 1500);
            }

            return Optional.of(cleanedText);

        } catch (Exception e) {
            log.warn("Web search failed for book '{}': {}. Falling back to internal content.", book.getTitle(), e.getMessage());
            return Optional.empty();
        }
    }
}
