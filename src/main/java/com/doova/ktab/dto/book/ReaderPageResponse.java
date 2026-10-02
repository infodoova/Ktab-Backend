package com.doova.ktab.dto.book;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single paginated reader view containing approximately N words (default 80) with reading progress metadata")
public record ReaderPageResponse(
        @Schema(description = "ID of the book", example = "121")
        Long bookId,

        @Schema(description = "Title of the book")
        String bookTitle,

        @Schema(description = "Current reader page number (1-based)", example = "1")
        int page,

        @Schema(description = "Total number of virtual reader pages in the book", example = "2450")
        int totalPages,

        @Schema(description = "Configured words per page (default 80)", example = "80")
        int wordsPerPage,

        @Schema(description = "Actual number of words on this page", example = "80")
        int wordCount,

        @Schema(description = "Starting global word index (0-based)", example = "0")
        long startWordIndex,

        @Schema(description = "Ending global word index (exclusive)", example = "80")
        long endWordIndex,

        @Schema(description = "Total word count across the entire book", example = "196000")
        long totalWords,

        @Schema(description = "Reading progress percentage (0.0 to 100.0)", example = "0.04")
        double progressPercentage,

        @Schema(description = "Title of the chapter/section containing this page", example = "مقدمة الناشر")
        String chapterTitle,

        @Schema(description = "Underlying physical PDF page number for cross-referencing", example = "19")
        Integer pdfPageNumber,

        @Schema(description = "The paginated text content (approximately 80 words of continuous Arabic text)")
        String content,

        @Schema(description = "True if this is the first page of the book")
        boolean isFirstPage,

        @Schema(description = "True if this is the last page of the book")
        boolean isLastPage,

        @Schema(description = "True if there is a next page")
        boolean hasNextPage,

        @Schema(description = "True if there is a previous page")
        boolean hasPreviousPage
) {
}
