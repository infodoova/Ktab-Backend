package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.SceneEngineResult;
import com.doova.ktab.features.story.enums.Beat;
import com.doova.ktab.features.story.enums.StoryLens;
import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.model.StoryConstitution;
import com.doova.ktab.features.story.service.impl.SceneEngineServiceImpl;
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
class SceneEngineServiceTest {

    @Spy
    private PromptFactory promptFactory = new PromptFactory();

    @Mock
    private SpringAiStoryClient aiClient;

    @InjectMocks
    private SceneEngineServiceImpl sceneEngineService;

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
    @DisplayName("generateScene_validAiResponse_returnsCompleteSceneEngineResult")
    void generateScene_validAiResponse_returnsCompleteSceneEngineResult() {
        String mockAiResponse = """
            <storyboard>
            dilemma: Confront or hide
            </storyboard>
            <script>
            تسلل الرجل بين أروقة السوق القديم بينما كانت أصوات الخيول تدنو من البوابة الكبرى. حبس أنفاسه وانتظر حتى مر الكشّاف.
            </script>
            <choices>
            [
              { "id": "A", "archetype": "CONFRONT", "text_ar": "يهاجم الكشاف مباغتة", "risk_profile": "GAMBLE" },
              { "id": "B", "archetype": "PROTECT", "text_ar": "يحمي الوثائق خلف الحائط", "risk_profile": "PERIL" },
              { "id": "C", "archetype": "MANIPULATE", "text_ar": "يرشو حارس الزقاق", "risk_profile": "STEADY" },
              { "id": "D", "archetype": "WITHDRAW", "text_ar": "يختبئ داخل القبو", "risk_profile": "SAFE" }
            ]
            </choices>
            <image_brief>
            { "frozen_frame_en": "A man hiding behind an ancient sandstone pillar in shadows", "characters_present": ["protagonist"] }
            </image_brief>
            <state_update>
            { "summary_append_ar": "نجح البطل في تفادي الدورية الأولى." }
            </state_update>
            """;

        when(aiClient.call(anyString())).thenReturn(mockAiResponse);

        SceneEngineResult result = sceneEngineService.generateScene(
                testStory,
                "{}",
                2,
                10,
                Beat.INCITING,
                4,
                "{}",
                "{}",
                "",
                "[]",
                "[]",
                "[]",
                "[]",
                "none",
                "none",
                "none",
                "none",
                "SUCCESS",
                "none"
        );

        assertThat(result).isNotNull();
        assertThat(result.script()).contains("تسلل الرجل بين أروقة السوق");
        assertThat(result.choices()).hasSize(4);
        assertThat(result.choices().get(0).id()).isEqualTo("A");
        assertThat(result.choices().get(0).archetype()).isEqualTo("CONFRONT");
        assertThat(result.choices().get(0).risk_profile()).isEqualTo("GAMBLE");
        assertThat(result.imageBrief()).isNotNull();
        assertThat(result.imageBrief().frozen_frame_en()).contains("pillar in shadows");
    }
}
