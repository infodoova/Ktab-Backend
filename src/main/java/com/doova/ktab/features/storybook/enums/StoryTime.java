package com.doova.ktab.features.storybook.enums;

public enum StoryTime {
    MORNING("soft morning sunrise and gentle early morning daylight"),
    DAYTIME("bright, cheerful, clear daytime with full natural sun"),
    AFTERNOON("warm afternoon sunlight with soft golden shadows"),
    SUNSET("sunset and golden hour with warm amber, orange, and gentle evening glow"),
    NIGHT("quiet nighttime with deep blue sky, soft moonlight, and shining stars");

    private final String sceneEn;

    StoryTime(String sceneEn) {
        this.sceneEn = sceneEn;
    }

    public String sceneEn() {
        return sceneEn;
    }
}
