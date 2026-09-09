package com.doova.ktab.features.story.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoryConstitution {

    private String settingTime;
    private String settingPlace;

    @Column(columnDefinition = "text")
    private String coreTheme;

    @Column(columnDefinition = "text")
    private String tone;

    @Column(columnDefinition = "text")
    private String philosophy;

    @Column(columnDefinition = "text")
    private String mainConflict;

    @Column(columnDefinition = "text")
    private String forbiddenElements;

    private String pacing;
}
