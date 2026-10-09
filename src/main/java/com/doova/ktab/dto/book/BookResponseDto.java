package com.doova.ktab.dto.book;

import com.doova.ktab.enums.status.BookStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookResponseDto {

    private Long id;

    private String title;
    private String description;
    private String language;

    private Integer ageRangeMin;
    private Integer ageRangeMax;
    private Integer pageCount;
    private Boolean hasAudio;

    private BigDecimal averageRating;
    private Integer totalReviews;

    private String coverImageUrl;

    /** The audio that introduces the book, when it has one. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private BookAboutAudioResponse aboutAudio;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String pdfDownloadUrl;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String pdfFileName;

    private String authorName;

    private BookStatus status;

    private Long mainGenreId;
    private String mainGenreName;

    private Long subGenreId;
    private String subGenreName;

    private Instant publishDate;

    private com.doova.ktab.enums.book.BookSource bookSource;
    private Long libraryOrganizationId;
    private String libraryOrganizationName;
    private String customAuthorName;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Instant submittedAt;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Instant reviewedAt;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String reviewNote;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String reviewedByName;
}
