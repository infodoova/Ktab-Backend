package com.doova.ktab.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookCoverResponse {
    private Long id;
    private String title;
    private String coverImageUrl;
    private String description;
    private String language;
    private Integer ageRangeMin;
    private Integer ageRangeMax;
    private Integer pageCount;
    private Instant publishDate;
    private String mainGenre;
    private String subGenre;
}
