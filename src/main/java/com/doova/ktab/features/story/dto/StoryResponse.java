package com.doova.ktab.features.story.dto;

import com.doova.ktab.features.story.enums.StoryLens;
import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.model.StoryConstitution;

public record  StoryResponse(
        Long id,
        String title,
        String genre,
        StoryLens lens,
        int sceneCount,
        StoryConstitution constitution,
        StoryVisualStyle visualStyle,
        String visualStyleNotes,
        String authorName,
        String coverImageUrl
) {
    public static StoryResponse from(Story story) {
        return new StoryResponse(
                story.getId(),
                story.getTitle(),
                story.getGenre(),
                story.getLens(),
                story.getSceneCount(),
                story.getConstitution(),
                story.getVisualStyle(),
                story.getVisualStyleNotes(),
                story.getAuthor().getFullName(),
                null // Cover URL will be set by service layer
        );
    }
    
    public static StoryResponse from(Story story, String coverImageUrl) {
        return new StoryResponse(
                story.getId(),
                story.getTitle(),
                story.getGenre(),
                story.getLens(),
                story.getSceneCount(),
                story.getConstitution(),
                story.getVisualStyle(),
                story.getVisualStyleNotes(),
                story.getAuthor().getFullName(),
                coverImageUrl
        );
    }
}
