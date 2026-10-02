package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CharacterBibleResponse(
        @com.fasterxml.jackson.annotation.JsonAlias({"cast", "character_list", "characterList", "character_bible"})
        @JsonPropertyDescription("List of character specifications with visual locks") List<CharacterVisualSpec> characters,
        @com.fasterxml.jackson.annotation.JsonAlias({"art_style", "artStyle", "style_notes", "styleNotes", "visual_style_notes", "style"})
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.doova.ktab.features.storybook.llm.LenientStringDeserializer.class)
        @JsonPropertyDescription("Global artistic and visual consistency notes") String visualStyleNotes,
        @com.fasterxml.jackson.annotation.JsonAlias({"overview", "cast_summary", "relationships", "cast_dynamics"})
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.doova.ktab.features.storybook.llm.LenientStringDeserializer.class)
        @JsonPropertyDescription("Brief summary of character relationships and cast dynamics") String summary
) implements com.doova.ktab.features.storybook.llm.ValidatedLlmResponse {
    @Override
    public java.util.List<String> problems() {
        if (characters == null || characters.isEmpty()) {
            return java.util.List.of("the character bible has no characters");
        }
        if (characters.stream().anyMatch(c -> c == null || c.name() == null || c.name().isBlank())) {
            return java.util.List.of("a character in the bible has no name");
        }
        return java.util.List.of();
    }

}
