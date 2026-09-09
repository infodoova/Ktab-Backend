package com.doova.ktab.dto.library;

import com.doova.ktab.enums.status.BookStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LibrarianBookUploadRequest {

    @NotBlank(message = "{validation.book.title.required}")
    @Size(max = 255, message = "{validation.book.title.size}")
    private String title;

    @Size(max = 2000, message = "{validation.book.description.size}")
    private String description;

    /**
     * The actual external/historical author of the book (e.g. "طه حسين")
     */
    @NotBlank(message = "{validation.book.author.required}")
    @Size(max = 255, message = "{validation.book.author.size}")
    private String customAuthorName;

    @NotNull(message = "{validation.book.main_genre.required}")
    private Long mainGenreId;

    @NotNull(message = "{validation.book.sub_genre.required}")
    private Long subGenreId;

    @Size(max = 50, message = "{validation.book.language.size}")
    private String language;

    @Min(value = 0, message = "{validation.book.age.min}")
    private Integer ageRangeMin;

    @Min(value = 0, message = "{validation.book.age.max}")
    private Integer ageRangeMax;

    @Min(value = 1, message = "{validation.book.page_count.min}")
    private Integer pageCount;

    @NotNull(message = "{validation.book.has_audio.required}")
    private Boolean hasAudio;

    @NotNull(message = "{validation.book.status.required}")
    private BookStatus status;
}
