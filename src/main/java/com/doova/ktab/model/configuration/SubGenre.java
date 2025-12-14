package com.doova.ktab.model.configuration;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_sub_genres")
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
}

