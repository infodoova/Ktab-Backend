package com.doova.ktab.api.dto.request;

import com.doova.ktab.api.validation.CreateBook;
import com.doova.ktab.enums.BookStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO used for creating or updating a Book entity.
 * Now converted from a record to a standard mutable class.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookRequestDto {

    // --- Core Content Fields ---
    @NotBlank(message = "Title is mandatory", groups = CreateBook.class)
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    private String description;

    @NotNull(groups = CreateBook.class)
    private Long mainGenreId;

    @NotNull(groups = CreateBook.class)
    private Long subGenreId;

    @Size(max = 50, message = "Language cannot exceed 50 characters", groups = CreateBook.class)
    private String language;

    // --- Numerical/Statistical Fields ---
    @Min(value = 0, message = "Minimum age range cannot be negative", groups = CreateBook.class)
    private Integer ageRangeMin;

    @Min(value = 0, message = "Maximum age range cannot be negative", groups = CreateBook.class)
    private Integer ageRangeMax;

    @Min(value = 1, message = "Page count must be at least 1", groups = CreateBook.class)
    private Integer pageCount;

    @NotNull(message = "Has audio status is mandatory", groups = CreateBook.class)
    private Boolean hasAudio;

    @NotNull(message = "Book status is mandatory for creation", groups = CreateBook.class)
    private BookStatus status;
}
