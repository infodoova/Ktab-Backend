package com.doova.ktab.features.storybook.blueprint;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.StorySetting;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record Blueprint(
        String key,
        int version,
        String titleAr,
        String titleEn,
        String theme,
        List<AgeBand> ageBands,
        List<StorySetting> allowedSettings,
        boolean religious,
        List<BlueprintBeat> beats
) {
    public static final Set<Integer> PAGE_COUNTS = Set.of(10, 12, 15);

    public static final String CUSTOM_KEY = "custom";

    /** A story built only from the user's inputs: no fixed beats, any age band, any setting. The blueprint step designs the structure. */
    public static Blueprint custom() {
        return new Blueprint(CUSTOM_KEY, 1, "قصة مخصصة", "Custom story", null,
                List.of(AgeBand.values()), List.of(StorySetting.values()), false, List.of());
    }

    public List<BlueprintBeat> beatsFor(int pageCount) {
        if (beats.isEmpty()) {
            return List.of();
        }
        if (!PAGE_COUNTS.contains(pageCount)) {
            throw new IllegalArgumentException("Page count must be 10, 12 or 15, was " + pageCount);
        }
        return beats.stream()
                .filter(b -> b.minPageCount() <= pageCount)
                .sorted(Comparator.comparingInt(BlueprintBeat::order))
                .toList();
    }
}
