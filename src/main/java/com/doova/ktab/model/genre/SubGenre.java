package com.doova.ktab.model.genre;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_sub_genres", uniqueConstraints = {@UniqueConstraint(name = "uq_sub_genre_name_ar_per_main", columnNames = {"name_ar", "main_genre_id"}), @UniqueConstraint(name = "uq_sub_genre_name_en_per_main", columnNames = {"name_en", "main_genre_id"})})
@Getter
@Setter
@NoArgsConstructor
public class SubGenre extends BaseEntity {

    private String nameEn;
    private String nameAr;
    private String description;

    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_genre_id", nullable = false)
    private MainGenre mainGenre;

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
}

