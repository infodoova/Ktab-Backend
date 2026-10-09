package com.doova.ktab.dto.book;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The audio that introduces a book, as shown with the book. The link is short-lived, like the cover and PDF links. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAboutAudioResponse {

    /** Where to stream or download the audio from; expires after a while, so fetch the book again for a fresh one. */
    private String url;

    private String description;

    /** Length in seconds, when it was provided. */
    private Integer durationSeconds;

    private String mimeType;
}
