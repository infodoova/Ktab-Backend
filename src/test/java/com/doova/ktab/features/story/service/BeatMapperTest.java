package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.BeatEntry;
import com.doova.ktab.features.story.enums.Beat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BeatMapperTest {

    @Test
    @DisplayName("buildBeatMap_with5Scenes_returnsHookIncitingCrisisClimaxResolution")
    void buildBeatMap_with5Scenes_returnsHookIncitingCrisisClimaxResolution() {
        List<Beat> map = BeatMapper.buildBeatMap(5);

        assertThat(map).containsExactly(
                Beat.HOOK,
                Beat.INCITING,
                Beat.CRISIS,
                Beat.CLIMAX,
                Beat.RESOLUTION
        );
    }

    @Test
    @DisplayName("buildBeatMap_with7Scenes_returnsExpectedStructureWithMidpoint")
    void buildBeatMap_with7Scenes_returnsExpectedStructureWithMidpoint() {
        List<Beat> map = BeatMapper.buildBeatMap(7);

        assertThat(map).containsExactly(
                Beat.HOOK,
                Beat.INCITING,
                Beat.RISING,
                Beat.MIDPOINT,
                Beat.CRISIS,
                Beat.CLIMAX,
                Beat.RESOLUTION
        );
    }

    @Test
    @DisplayName("buildBeatMap_with10Scenes_returnsStandard10SceneArc")
    void buildBeatMap_with10Scenes_returnsStandard10SceneArc() {
        List<Beat> map = BeatMapper.buildBeatMap(10);

        assertThat(map).containsExactly(
                Beat.HOOK,
                Beat.INCITING,
                Beat.RISING,
                Beat.RISING,
                Beat.MIDPOINT,
                Beat.TIGHTENING,
                Beat.TIGHTENING,
                Beat.CRISIS,
                Beat.CLIMAX,
                Beat.RESOLUTION
        );
    }

    @Test
    @DisplayName("tension_acrossBeats_matchesTargetCurve")
    void tension_acrossBeats_matchesTargetCurve() {
        assertThat(BeatMapper.tension(Beat.HOOK, 1, 10)).isEqualTo(3);
        assertThat(BeatMapper.tension(Beat.INCITING, 2, 10)).isEqualTo(4);
        assertThat(BeatMapper.tension(Beat.MIDPOINT, 5, 10)).isEqualTo(6);
        assertThat(BeatMapper.tension(Beat.TIGHTENING, 6, 10)).isEqualTo(7);
        assertThat(BeatMapper.tension(Beat.CRISIS, 8, 10)).isEqualTo(8);
        assertThat(BeatMapper.tension(Beat.CLIMAX, 9, 10)).isEqualTo(10);
        assertThat(BeatMapper.tension(Beat.RESOLUTION, 10, 10)).isEqualTo(5);
    }

    @Test
    @DisplayName("buildFullBeatMap_buildsListOfBeatEntriesWithCorrectTension")
    void buildFullBeatMap_buildsListOfBeatEntriesWithCorrectTension() {
        List<BeatEntry> fullMap = BeatMapper.buildFullBeatMap(5);

        assertThat(fullMap).hasSize(5);
        assertThat(fullMap.get(0).scene()).isEqualTo(1);
        assertThat(fullMap.get(0).beat()).isEqualTo(Beat.HOOK);
        assertThat(fullMap.get(0).tension()).isEqualTo(3);

        assertThat(fullMap.get(3).scene()).isEqualTo(4);
        assertThat(fullMap.get(3).beat()).isEqualTo(Beat.CLIMAX);
        assertThat(fullMap.get(3).tension()).isEqualTo(10);
    }

    @Test
    @DisplayName("getBeatInstructions_forAllBeats_returnsNonEmptyInstructionBlocks")
    void getBeatInstructions_forAllBeats_returnsNonEmptyInstructionBlocks() {
        for (Beat beat : Beat.values()) {
            String instructions = BeatMapper.getBeatInstructions(beat);
            assertThat(instructions).isNotBlank().contains("<beat");
        }
    }
}
