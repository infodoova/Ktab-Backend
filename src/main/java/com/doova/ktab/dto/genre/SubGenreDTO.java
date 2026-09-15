package com.doova.ktab.dto.genre;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubGenreDTO {

    private Long id;

    @Size(max = 100, message = "{validation.genre.name.size}")
    private String name;

    @Size(max = 100, message = "{validation.genre.name_en.size}")
    private String nameEn;

    @Size(max = 100, message = "{validation.genre.name_ar.size}")
    private String nameAr;

    @Size(max = 500, message = "{validation.genre.description.size}")
    private String description;

    public SubGenreDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "{validation.genre.name.required}")
    public boolean isNameProvided() {
        return (name != null && !name.isBlank()) ||
               (nameAr != null && !nameAr.isBlank()) ||
               (nameEn != null && !nameEn.isBlank());
    }
}
