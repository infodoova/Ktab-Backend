package com.doova.ktab.dto.genre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubGenreDTO {

    private Long id;

    @NotBlank(message = "{validation.genre.name_en.required}")
    @Size(max = 100, message = "{validation.genre.name_en.size}")
    private String nameEn;

    @NotBlank(message = "{validation.genre.name_ar.required}")
    @Size(max = 100, message = "{validation.genre.name_ar.size}")
    private String nameAr;

    @Size(max = 500, message = "{validation.genre.description.size}")
    private String description;
}

