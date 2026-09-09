package com.doova.ktab.dto.library;

import com.doova.ktab.enums.status.BookStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLibrarianBookRequest {

    @Size(max = 255, message = "{validation.book.title.size}")
    private String title;

    @Size(max = 2000, message = "{validation.book.description.size}")
    private String description;

    @Size(max = 255, message = "{validation.book.author.size}")
    private String customAuthorName;

    private Long mainGenreId;
    private Long subGenreId;

    @Size(max = 50, message = "{validation.book.language.size}")
    private String language;

    @Min(value = 0, message = "{validation.book.age.min}")
    private Integer ageRangeMin;

    @Min(value = 0, message = "{validation.book.age.max}")
    private Integer ageRangeMax;

    @Min(value = 1, message = "{validation.book.page_count.min}")
    private Integer pageCount;

    private Boolean hasAudio;
    private BookStatus status;
}
