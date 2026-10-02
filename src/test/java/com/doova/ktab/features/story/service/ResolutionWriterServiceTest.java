package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.ResolutionResult;
import com.doova.ktab.features.story.enums.StoryLens;
import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.model.StoryConstitution;
import com.doova.ktab.features.story.service.impl.ResolutionWriterServiceImpl;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolutionWriterServiceTest {

    @Spy
    private PromptFactory promptFactory = new PromptFactory();

    @Mock
    private SpringAiStoryClient aiClient;

    @InjectMocks
    private ResolutionWriterServiceImpl resolutionWriterService;

    private Story testStory;

    @BeforeEach
    void setUp() {
        User author = User.builder().firstName("Ali").lastName("Hassan").role("AUTHOR").build();
        author.setId(1L);
        StoryConstitution constitution = new StoryConstitution(
                "Baghdad 1258", "Old Baghdad", "Survival", "Dark",
                "Fatalism", "Siege", "Magic", "Fast"
        );
        testStory = new Story(author, "Siege of Baghdad", "Thriller", 10, StoryLens.SURVIVAL, constitution, StoryVisualStyle.CINEMATIC_STORYBOOK, "Oil painting");
        testStory.setId(10L);
    }

    @Test
    @DisplayName("writeResolution_validAiResponse_returnsResolutionResultWithEnding")
    void writeResolution_validAiResponse_returnsResolutionResultWithEnding() {
        String mockResponse = """
            <storyboard>
            chosen_ending: e1
            </storyboard>
            <script>
            انقشع غبار المعركة ببطء عن أسوار المدينة، ووقف البطل شاهداً على ما تبقى من الحكاية.
            </script>
            <image_brief>
            { "frozen_frame_en": "A figure standing before the distant sunrise over quiet ruined city walls" }
            </image_brief>
            <ending>
            { "id": "e1", "type": "BITTERSWEET", "epilogue_line_ar": "النهاية التي تخلد الذكرى" }
            </ending>
            """;

        when(aiClient.call(anyString())).thenReturn(mockResponse);

        ResolutionResult result = resolutionWriterService.writeResolution(
                testStory,
                "{}",
                "{}",
                "ملخص الرحلة السابقة",
                "[]",
                "يقتحم الممر الأخير",
                "GAMBLE",
                "SUCCESS",
                "e1"
        );

        assertThat(result).isNotNull();
        assertThat(result.script()).contains("انقشع غبار المعركة");
        assertThat(result.ending()).isNotNull();
        assertThat(result.ending().id()).isEqualTo("e1");
        assertThat(result.ending().type()).isEqualTo("BITTERSWEET");
        assertThat(result.ending().epilogue_line_ar()).isEqualTo("النهاية التي تخلد الذكرى");
    }
}
