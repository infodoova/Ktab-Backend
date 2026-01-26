package com.doova.ktab.dto.genre;

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
    private String nameEn;
    private String nameAr;
    private String description;
    private List<SubGenreDTO> subGenres;
}
