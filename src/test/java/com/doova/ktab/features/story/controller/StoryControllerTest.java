package com.doova.ktab.features.story.controller;

import com.doova.ktab.features.story.dto.StoryResponse;
import com.doova.ktab.features.story.enums.StoryLens;
import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.features.story.model.StoryConstitution;
import com.doova.ktab.features.story.service.StoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StoryControllerTest {

    @Mock
    private StoryService storyService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoryController storyController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storyController).build();
        when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("getAllStoriesPaged_validRequest_returns200AndStoryResponses")
    void getAllStoriesPaged_validRequest_returns200AndStoryResponses() throws Exception {
        StoryConstitution constitution = new StoryConstitution(
                "Modern", "Cairo", "Theme", "Tone",
                "Philosophy", "Conflict", "None", "Fast"
        );
        StoryResponse storyResponse = new StoryResponse(
                100L,
                "Cairo Trilogy",
                "Drama",
                StoryLens.PSYCHOLOGICAL,
                10,
                constitution,
                StoryVisualStyle.CINEMATIC_STORYBOOK,
                "Notes",
                "Najib Mahfouz",
                "https://example.com/cover.png"
        );

        when(storyService.getAllStoriesPaged(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(storyResponse), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/stories")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(100))
                .andExpect(jsonPath("$.data.content[0].title").value("Cairo Trilogy"))
                .andExpect(jsonPath("$.data.content[0].authorName").value("Najib Mahfouz"))
                .andExpect(jsonPath("$.data.content[0].coverImageUrl").value("https://example.com/cover.png"));
    }

    @Test
    @DisplayName("getStoryById_existingStory_returns200AndStoryResponse")
    void getStoryById_existingStory_returns200AndStoryResponse() throws Exception {
        StoryConstitution constitution = new StoryConstitution(
                "Modern", "Cairo", "Theme", "Tone",
                "Philosophy", "Conflict", "None", "Fast"
        );
        StoryResponse storyResponse = new StoryResponse(
                100L,
                "Cairo Trilogy",
                "Drama",
                StoryLens.PSYCHOLOGICAL,
                10,
                constitution,
                StoryVisualStyle.CINEMATIC_STORYBOOK,
                "Notes",
                "Najib Mahfouz",
                "https://example.com/cover.png"
        );

        when(storyService.getStoryById(100L)).thenReturn(storyResponse);

        mockMvc.perform(get("/stories/100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.title").value("Cairo Trilogy"))
                .andExpect(jsonPath("$.data.authorName").value("Najib Mahfouz"));
    }
}
