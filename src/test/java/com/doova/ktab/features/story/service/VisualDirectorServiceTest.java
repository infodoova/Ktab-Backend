package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.ImageBrief;
import com.doova.ktab.features.story.dto.VisualDirectorOutput;
import com.doova.ktab.features.story.enums.Beat;
import com.doova.ktab.features.story.service.impl.VisualDirectorServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisualDirectorServiceTest {

    @Spy
    private PromptFactory promptFactory = new PromptFactory();

    @Mock
    private SpringAiStoryClient aiClient;

    @InjectMocks
    private VisualDirectorServiceImpl visualDirectorService;

    @Test
    @DisplayName("directImage_validAiResponse_returnsVisualDirectorOutputWithCameraAndPrompt")
    void directImage_validAiResponse_returnsVisualDirectorOutputWithCameraAndPrompt() {
        String mockResponse = """
            {
              "prompt": "Cinematic oil painting, medium shot, 35mm lens, middle eastern protagonist standing near an ancient gate with lantern",
              "negative_prompt": "text, watermark, deformed fingers",
              "aspect_ratio": "1:1",
              "camera": { "shot": "medium", "lens_mm": 35, "angle": "eye level" }
            }
            """;

        when(aiClient.callFast(anyString())).thenReturn(mockResponse);

        ImageBrief brief = new ImageBrief(
                "A tense standoff at the market gate",
                List.of("protagonist"),
                List.of(),
                "l1",
                "dusk",
                "clear",
                "tense",
                "lantern"
        );

        VisualDirectorOutput result = visualDirectorService.directImage(
                "{}",
                "Cinematic Oil Painting",
                "Earthy tones",
                "",
                brief,
                Beat.HOOK,
                3,
                1,
                10
        );

        assertThat(result).isNotNull();
        assertThat(result.prompt()).containsIgnoringCase("Cinematic oil painting");
        assertThat(result.aspect_ratio()).isEqualTo("1:1");
        assertThat(result.camera()).isNotNull();
        assertThat(result.camera().lens_mm()).isEqualTo(35);
    }
}
