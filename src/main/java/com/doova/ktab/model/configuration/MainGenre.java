package com.doova.ktab.model.configuration;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_main_genres", uniqueConstraints = {@UniqueConstraint(name = "uq_main_genre_name_ar", columnNames = "name_ar"), @UniqueConstraint(name = "uq_main_genre_name_en", columnNames = "name_en")})

@Getter
@Setter
@NoArgsConstructor
public class MainGenre extends BaseEntity {


    private String nameEn;
    private String nameAr;
    private String description;

    private boolean active = true;

    @OneToMany(mappedBy = "mainGenre", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubGenre> subGenres = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (nameAr != null) {
            nameAr = nameAr.trim();
        }
        if (nameEn != null) {
            nameEn = nameEn.trim();
        }
    }

    public void addSubGenre(SubGenre subGenre) {
        subGenres.add(subGenre);
        subGenre.setMainGenre(this);
    }

    public void removeSubGenre(SubGenre subGenre) {
        subGenres.remove(subGenre);
        subGenre.setMainGenre(null);
    }


    public void clearSubGenres() {
        this.subGenres.clear();
    }
}

