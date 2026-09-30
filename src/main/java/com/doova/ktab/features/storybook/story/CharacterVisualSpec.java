package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.llm.LenientStringDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CharacterVisualSpec(
        @JsonPropertyDescription("Name of the character") String name,
        @JsonPropertyDescription("Role: PROTAGONIST, COMPANION, PARENT, FRIEND, GUIDE") String role,
        @JsonAlias({"lockedVisualIdentity", "visual_lock", "appearance", "locked_visual_identity", "visualDetails"})
        @JsonDeserialize(using = LenientStringDeserializer.class)
        @JsonPropertyDescription("Locked facial features, hair, eyes, skin tone, glasses/headwear") String visualLock,
        @JsonDeserialize(using = LenientStringDeserializer.class)
        @JsonPropertyDescription("Locked signature outfit and colors (identical across all pages)") String clothing,
        @JsonDeserialize(using = LenientStringDeserializer.class)
        @JsonPropertyDescription("Core personality traits and emotional demeanor") String personality
) {
}
