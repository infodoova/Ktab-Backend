package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.story.CharacterInScene;

import java.util.ArrayList;
import java.util.List;

public final class ReferenceAssembler {

    private ReferenceAssembler() {
    }

    /** Order is CHILD sheet, style reference, then COMPANION sheet — VisualQa relies on it. */
    public static List<ReferenceImage> forPage(byte[] childSheet, byte[] styleRef, byte[] companionSheet,
                                               List<CharacterInScene> cast) {
        List<ReferenceImage> refs = new ArrayList<>();
        refs.add(new ReferenceImage(childSheet, "image/png"));
        refs.add(new ReferenceImage(styleRef, "image/png"));
        boolean companionInScene = cast != null && cast.stream().anyMatch(c -> "COMPANION".equals(c.ref()));
        if (companionSheet != null && companionInScene) {
            refs.add(new ReferenceImage(companionSheet, "image/png"));
        }
        return refs;
    }
}
