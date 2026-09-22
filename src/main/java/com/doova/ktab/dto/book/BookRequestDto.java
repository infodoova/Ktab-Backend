package com.doova.ktab.dto.book;

import com.doova.ktab.validation.CreateBook;
import com.doova.ktab.enums.status.BookStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @NotBlank(message = "{validation.book.title.required}", groups = CreateBook.class)
    @Size(max = 255, message = "{validation.book.title.size}")
    private String title;

    @Size(max = 2000, message = "{validation.book.description.size}")
    private String description;

    @NotNull(message = "{validation.book.main_genre.required}", groups = CreateBook.class)
    private Long mainGenreId;

    @NotNull(message = "{validation.book.sub_genre.required}", groups = CreateBook.class)
    private Long subGenreId;

    @Size(max = 50, message = "{validation.book.language.size}", groups = CreateBook.class)
    private String language;

    // --- Numerical/Statistical Fields ---
    @Min(value = 0, message = "{validation.book.age.min}", groups = CreateBook.class)
    private Integer ageRangeMin;

    @Min(value = 0, message = "{validation.book.age.max}", groups = CreateBook.class)
    private Integer ageRangeMax;

    @Min(value = 1, message = "{validation.book.page_count.min}", groups = CreateBook.class)
    private Integer pageCount;

    @NotNull(message = "{validation.book.has_audio.required}", groups = CreateBook.class)
    private Boolean hasAudio;

    /**
     * @deprecated Book status is no longer author-assignable. Books always start as
     * DRAFT and progress through the editorial review workflow
     * (submit -> UNDER_REVIEW -> publisher approves -> PUBLISHED). Setting this field
     * to anything other than DRAFT (or leaving it null) is required; any other value
     * is rejected by {@code BookServiceImpl}. Kept only for backward-compatible
     * deserialization of older clients.
     */
    @Deprecated
    @Schema(deprecated = true, description = "Deprecated: status is no longer author-assignable; books always start as DRAFT.")
    private BookStatus status;
}
