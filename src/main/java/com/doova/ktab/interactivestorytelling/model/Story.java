package com.doova.ktab.interactivestorytelling.model;

import com.doova.ktab.interactivestorytelling.enums.StoryLens;
import com.doova.ktab.interactivestorytelling.enums.StoryVisualStyle;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tbl_stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Story extends BaseEntity {

    @Column(name = "col_title", nullable = false)
    private String title;

    @Column(name = "col_genre")
    private String genre;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_story_lens", nullable = false)
    private StoryLens lens;

    @Min(1)
    @Column(name = "col_scene_count", nullable = false)
    private int sceneCount;

    @Embedded
    private StoryConstitution constitution;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_visual_style")
    private StoryVisualStyle visualStyle = StoryVisualStyle.CINEMATIC_STORYBOOK;

    @Column(name = "col_visual_style_notes", columnDefinition = "text")
    private String visualStyleNotes;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_story_author"))
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User author;

    public Story(User author, String title, String genre, int sceneCount, StoryLens lens, StoryConstitution constitution,StoryVisualStyle visualStyle,String visualStyleNotes) {
        this.author = author;
        this.title = title;
        this.genre = genre;
        this.visualStyle=visualStyle;
        this.visualStyleNotes=visualStyleNotes;
        this.sceneCount = sceneCount;
        this.lens = lens;
        this.constitution = constitution;
    }
}
