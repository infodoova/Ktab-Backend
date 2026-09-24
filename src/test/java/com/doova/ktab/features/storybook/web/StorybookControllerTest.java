package com.doova.ktab.features.storybook.web;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.web.dto.BlueprintSummary;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StorybookControllerTest {

    private final StorybookService service = mock(StorybookService.class);
    private final MessageSource messages = mock(MessageSource.class);
    private final com.doova.ktab.features.storybook.orchestrator.StorybookResumeService resumeService = mock(com.doova.ktab.features.storybook.orchestrator.StorybookResumeService.class);
    private final com.doova.ktab.features.storybook.story.pipeline.StoryApprovalService storyApprovalService = mock(com.doova.ktab.features.storybook.story.pipeline.StoryApprovalService.class);
    private final com.doova.ktab.features.storybook.character.PhotoIntakeService photoIntakeService = mock(com.doova.ktab.features.storybook.character.PhotoIntakeService.class);
    private final com.doova.ktab.features.storybook.illustration.LookService lookService = mock(com.doova.ktab.features.storybook.illustration.LookService.class);
    private MockMvc mvc;
    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(1L);
        HandlerMethodArgumentResolver currentUser = new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter p) { return p.hasParameterAnnotation(CurrentUser.class); }
            public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory f) { return user; }
        };
        mvc = MockMvcBuilders.standaloneSetup(new StorybookController(service, messages, resumeService, storyApprovalService, photoIntakeService, lookService))
                .setCustomArgumentResolvers(currentUser).build();
    }

    private static StorybookDetail detail() {
        return new StorybookDetail(42L, StorybookStatus.DRAFT, null, "سامي", LanguageVariety.MSA, TashkeelLevel.FULL,
                10, null, List.of(), null, null, 2, 3);
    }

    @Test
    void createReturns201WithTheDraft() throws Exception {
        when(service.create(eq(user), any())).thenReturn(detail());
        mvc.perform(post("/storybook/books").contentType(MediaType.APPLICATION_JSON).content("""
                {"childProfileId":5,"blueprintKey":"first-day-of-school","style":"SOFT_WATERCOLOR",
                 "pageCount":10,"variety":"MSA","tashkeelLevel":"FULL"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createWithoutPageCountIs400() throws Exception {
        mvc.perform(post("/storybook/books").contentType(MediaType.APPLICATION_JSON).content("""
                {"childProfileId":5,"blueprintKey":"first-day-of-school","style":"SOFT_WATERCOLOR"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsTheBook() throws Exception {
        when(service.detail(user, 42L)).thenReturn(detail());
        mvc.perform(get("/storybook/books/42")).andExpect(status().isOk()).andExpect(jsonPath("$.data.childNameAr").value("سامي"));
    }

    @Test
    void blueprintsAreFilteredByAgeBand() throws Exception {
        when(service.blueprints(AgeBand.AGE_3_5)).thenReturn(List.of(
                new BlueprintSummary("first-day-of-school", "يومي الأول في المدرسة", "My First Day at School", "school", false, List.of())));
        mvc.perform(get("/storybook/blueprints").param("ageBand", "AGE_3_5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].key").value("first-day-of-school"));
    }

    @Test
    void resumeReturns202Accepted() throws Exception {
        mvc.perform(post("/storybook/books/42/resume"))
                .andExpect(status().isAccepted());
    }

    @Test
    void approveStoryReturns202Accepted() throws Exception {
        mvc.perform(post("/storybook/books/42/story/approve"))
                .andExpect(status().isAccepted());
    }

    @Test
    void uploadPhotoReturns202Accepted() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "photo", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/storybook/books/42/photo")
                        .file(file)
                        .param("consent", "true"))
                .andExpect(status().isAccepted());
    }

    @Test
    void regenerateLookReturns202Accepted() throws Exception {
        mvc.perform(post("/storybook/books/42/character/regenerate"))
                .andExpect(status().isAccepted());
    }

    @Test
    void approveLookReturns202Accepted() throws Exception {
        mvc.perform(post("/storybook/books/42/character/approve"))
                .andExpect(status().isAccepted());
    }
}
