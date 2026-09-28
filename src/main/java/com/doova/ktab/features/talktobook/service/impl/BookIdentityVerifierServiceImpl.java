package com.doova.ktab.features.talktobook.service.impl;

import com.doova.ktab.features.talktobook.service.BookIdentityVerifierService;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookIdentityVerifierServiceImpl implements BookIdentityVerifierService {

    private final BookSectionRepository bookSectionRepository;

    @Override
    public boolean verifyBookIdentity(Book book, String webContent) {
        if (webContent == null || webContent.isBlank()) {
            return false;
        }

        String normWeb = normalizeArabic(webContent);
        String normTitle = normalizeArabic(book.getTitle());

        // Extract main title before colon or dash if present
        String rawTitle = book.getTitle();
        String mainTitle = rawTitle;
        if (rawTitle.contains(":")) {
            mainTitle = rawTitle.split(":")[0];
        } else if (rawTitle.contains("-")) {
            mainTitle = rawTitle.split("-")[0];
        }
        String normMainTitle = normalizeArabic(mainTitle);

        boolean titleMatched = (!normTitle.isBlank() && normWeb.contains(normTitle))
                || (!normMainTitle.isBlank() && normMainTitle.length() >= 4 && normWeb.contains(normMainTitle));

        if (!titleMatched) {
            log.info("Book identity verification rejected: Neither full title nor main title '{}' found in web content", mainTitle);
            return false;
        }

        // 2. Author verification if author name is present
        String authorName = book.getCustomAuthorName() != null ? book.getCustomAuthorName() :
                (book.getAuthor() != null ? book.getAuthor().getFirstName() + " " + book.getAuthor().getLastName() : null);

        if (authorName != null && !authorName.isBlank()) {
            String normAuthor = normalizeArabic(authorName);
            String[] authorParts = normAuthor.split("\\s+");
            for (String part : authorParts) {
                if (part.length() >= 3 && normWeb.contains(part)) {
                    log.debug("Book identity confirmed via Title and Author matching ('{}')", part);
                    return true;
                }
            }
        }

        // 3. Description keyword verification if description is present
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            String normDesc = normalizeArabic(book.getDescription());
            for (String word : normDesc.split("\\s+")) {
                if (word.length() >= 5 && normWeb.contains(word)) {
                    log.debug("Book identity confirmed via Title and Description keyword matching ('{}')", word);
                    return true;
                }
            }
        }

        // 4. Fallback: Table of contents section title matching
        List<BookSection> sections = bookSectionRepository.findByBook_IdOrderBySortOrderAsc(book.getId());
        for (BookSection section : sections) {
            String normSection = normalizeArabic(section.getTitle());
            if (normSection.length() >= 4 && normWeb.contains(normSection)) {
                log.debug("Book identity confirmed via Section Title matching: '{}'", section.getTitle());
                return true;
            }
        }

        // 5. If main title is specific and long enough (>= 10 chars), accept match
        if (normMainTitle.length() >= 10) {
            log.debug("Book identity confirmed via distinct title matching ('{}')", normMainTitle);
            return true;
        }

        log.info("Book identity verification inconclusive for '{}'. Rejecting web search to avoid hallucination.", book.getTitle());
        return false;
    }

    private String normalizeArabic(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\u064B-\\u065F\\u0670]", "") // strip tashkeel
                .replaceAll("[أإآٱ]", "ا")
                .replaceAll("ة", "ه")
                .replaceAll("ى", "ي")
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
