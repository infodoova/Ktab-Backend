package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.ChildGender;

import java.util.List;

public record CharacterInput(
        String id,
        String name,
        String type,
        Integer age,
        ChildGender gender,
        String role,
        String relationship,
        List<String> personality,
        ChildAppearance appearance,
        String clothes,
        String strength,
        String weakness,
        String favoriteActivity,
        String signatureItem,
        String speakingStyle,
        String photoBase64
) {
    public CharacterInput(String id, String name, String type, Integer age, ChildGender gender,
                          String role, String relationship, List<String> personality,
                          ChildAppearance appearance, String clothes, String strength,
                          String weakness, String favoriteActivity, String signatureItem,
                          String speakingStyle) {
        this(id, name, type, age, gender, role, relationship, personality, appearance, clothes,
                strength, weakness, favoriteActivity, signatureItem, speakingStyle, null);
    }

    public CharacterInput(String id, String name, String type, String role, String relationship,
                          String clothes, List<String> personality, ChildAppearance appearance) {
        this(id, name, type, null, null, role, relationship, personality, appearance, clothes,
                null, null, null, null, null, null);
    }
}
