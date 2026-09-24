package com.doova.ktab.features.storybook.enums;

public enum StorySetting {
    BEIRUT("Beirut, Lebanon: sea promenade, old stone houses with red roofs, mountains behind"),
    CAIRO("Cairo, Egypt: the Nile, busy friendly streets, palm trees, the pyramids in the far distance"),
    RIYADH("Riyadh, Saudi Arabia: modern towers, desert-coloured houses, palm-lined streets"),
    DUBAI("Dubai, UAE: tall glass towers, the creek with wooden boats, sandy beaches"),
    AMMAN("Amman, Jordan: white stone houses on hills, stairs and narrow lanes"),
    GENERIC_CITY("a friendly Arab city with colourful houses and small shops"),
    COUNTRYSIDE("green countryside with olive trees, a small village and hills");

    private final String sceneEn;
    StorySetting(String sceneEn) { this.sceneEn = sceneEn; }
    public String sceneEn() { return sceneEn; }
}
