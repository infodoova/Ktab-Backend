package com.doova.ktab.features.talktobook.util;

import java.util.Locale;

/** Recognizes simple author-name requests that are fully answered by book metadata. */
public final class BookMetadataQuestion {
    private BookMetadataQuestion() {}

    public static boolean asksAuthorName(String question) {
        if (question == null || question.isBlank()) return false;
        String normalized = question.toLowerCase(Locale.ROOT)
                .replaceAll("[\u064B-\u065F\u0670\u0640]", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        boolean english = normalized.matches(
                "(?:what is (?:the )?(?:name of (?:the )?author|author s name)"
                + "|who (?:is|was) (?:the )?author|who wrote"
                + "|(?:the )?author(?: s)? name|name of (?:the )?author)"
                + "(?: (?:of|for)?)?(?: (?:this|the) books?)?"
        );
        boolean arabic = normalized.matches(
                "(?:من (?:هو |هي )?(?:مؤلف|كاتب)|ما اسم (?:ال)?(?:مؤلف|كاتب)"
                + "|اسم (?:ال)?(?:مؤلف|كاتب)|من كتب)"
                + "(?: (?:هذا|هذه))?(?: (?:الكتاب|الرواية|كتاب|رواية))?"
        );
        return english || arabic;
    }
}
