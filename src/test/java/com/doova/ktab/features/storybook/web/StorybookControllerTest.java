package com.doova.ktab.features.storybook.web;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.features.storybook.web.dto.StorybookSummary;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    private final com.doova.ktab.features.storybook.illustration.PageRegenerationService pageRegenerationService = mock(com.doova.ktab.features.storybook.illustration.PageRegenerationService.class);
    private final com.doova.ktab.features.storybook.render.ReaderService readerService = mock(com.doova.ktab.features.storybook.render.ReaderService.class);
    private final CancelService cancelService = mock(CancelService.class);
    private MockMvc mvc;
    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(1L);
        HandlerMethodArgumentResolver currentUser = new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter p) { return p.hasParameterAnnotation(CurrentUser.class); }
            public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory f) { return user; }
        };
        mvc = MockMvcBuilders.standaloneSetup(new StorybookController(service, messages, resumeService, storyApprovalService,
                photoIntakeService, lookService, pageRegenerationService, readerService, cancelService))
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
                {"childProfileId":5,"style":"SOFT_WATERCOLOR",
                 "pageCount":15,"variety":"MSA","tashkeelLevel":"FULL"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createMultipartReturns201WithTheDraft() throws Exception {
        when(service.create(eq(user), any(), any(), any(), any(), any())).thenReturn(detail());
        org.springframework.mock.web.MockMultipartFile jsonPart = new org.springframework.mock.web.MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE, """
                {"childProfileId":5,"style":"SOFT_WATERCOLOR",
                 "pageCount":15,"variety":"MSA","tashkeelLevel":"FULL"}""".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.springframework.mock.web.MockMultipartFile photoPart = new org.springframework.mock.web.MockMultipartFile(
                "childPhoto", "child.jpg", "image/jpeg", "dummy-jpeg-data".getBytes());

        mvc.perform(multipart("/storybook/books")
                        .file(jsonPart)
                        .file(photoPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createWithoutPageCountIs400() throws Exception {
        mvc.perform(post("/storybook/books").contentType(MediaType.APPLICATION_JSON).content("""
                {"childProfileId":5,"style":"SOFT_WATERCOLOR"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsTheBook() throws Exception {
        when(service.detail(user, 42L)).thenReturn(detail());
        mvc.perform(get("/storybook/books/42")).andExpect(status().isOk()).andExpect(jsonPath("$.data.childNameAr").value("سامي"));
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

    @Test
    void regeneratePageReturns202Accepted() throws Exception {
        mvc.perform(post("/storybook/books/42/pages/3/regenerate"))
                .andExpect(status().isAccepted());
    }

    @Test
    void getReaderReturns200Ok() throws Exception {
        when(readerService.manifest(eq(user), eq(42L))).thenReturn(
                new com.doova.ktab.features.storybook.render.ReaderManifest(
                        42L, "rtl", "عنوان", List.of()));
        mvc.perform(get("/storybook/books/42/reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookId").value(42))
                .andExpect(jsonPath("$.data.dir").value("rtl"));
    }

    @Test
    void getDownloadUrlReturns200Ok() throws Exception {
        when(readerService.downloadUrl(eq(user), eq(42L))).thenReturn("https://r2.ktab.app/pdf");
        mvc.perform(get("/storybook/books/42/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://r2.ktab.app/pdf"));
    }

    @Test
    void cancelReturns202Accepted() throws Exception {
        mvc.perform(post("/storybook/books/42/cancel"))
                .andExpect(status().isAccepted());
    }

    @Test
    void listReturns200WithSummariesIncludingCoverImageUrl() throws Exception {
        when(service.list(user)).thenReturn(List.of(
                new StorybookSummary(42L, "سِرُّ الْبَحْرِ", "سامي", StorybookStatus.READY,
                        16, "https://signed/covers/42.png", java.time.LocalDateTime.now())
        ));

        mvc.perform(get("/storybook/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(42))
                .andExpect(jsonPath("$.data[0].titleAr").value("سِرُّ الْبَحْرِ"))
                .andExpect(jsonPath("$.data[0].coverImageUrl").value("https://signed/covers/42.png"));
    }

    @Test
    void editStoryReturns200WithUpdatedDetail() throws Exception {
        when(service.editStory(eq(user), eq(42L), any())).thenReturn(detail());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/storybook/books/42/story")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titleAr": "مغامرة سامي في الحديقة",
                                  "pages": [
                                    {"pageIndex": 1, "textAr": "ذهب سامي إلى الحديقة.", "sceneEn": "Sami in the garden."}
                                  ]
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42));
    }
}
