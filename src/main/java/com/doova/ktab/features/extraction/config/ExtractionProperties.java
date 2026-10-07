package com.doova.ktab.features.extraction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ktab.extraction")
@Getter
@Setter
public class ExtractionProperties {

    /** Largest PDF accepted by POST /api/v1/books/extract and the native ingestion job. */
    private long maxUploadBytes = 200L * 1024 * 1024;
    /** A chapter shorter than this many characters gets a SHORT_CHAPTER warning. */
    private int shortChapterChars = 300;
    /** With 3+ chapters, one covering more than this share of the book gets a LARGE_GAP warning. */
    private double largeGapRatio = 0.6;
    /** Below this share of Arabic letters the extraction is treated as garbled (an error). */
    private double minArabicRatio = 0.5;
}
