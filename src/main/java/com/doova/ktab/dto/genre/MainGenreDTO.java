package com.doova.ktab.dto.genre;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MainGenreDTO {

    private Long id;

    @NotBlank(message = "{validation.genre.name_en.required}")
    @Size(max = 100, message = "{validation.genre.name_en.size}")
    private String nameEn;

    @NotBlank(message = "{validation.genre.name_ar.required}")
    @Size(max = 100, message = "{validation.genre.name_ar.size}")
    private String nameAr;

    @Size(max = 500, message = "{validation.genre.description.size}")
    private String description;

    private List<@Valid SubGenreDTO> subGenres;
}
