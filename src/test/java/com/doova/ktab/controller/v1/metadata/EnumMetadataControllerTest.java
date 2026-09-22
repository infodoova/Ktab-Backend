package com.doova.ktab.controller.v1.metadata;

import com.doova.ktab.dto.metadata.AppEnumsResponseDto;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.*;
import com.doova.ktab.service.metadata.MetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EnumMetadataControllerTest {

    @Mock
    private MetadataService metadataService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private EnumMetadataController enumMetadataController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(enumMetadataController).build();
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("getAllEnums_validRequest_returnsFullMetadataPayload")
    void getAllEnums_validRequest_returnsFullMetadataPayload() throws Exception {
        AppEnumsResponseDto dto = new AppEnumsResponseDto(
                List.of(
                        new RoleMetadataDto("AUTHOR", "10", "مؤلف", "Author"),
                        new RoleMetadataDto("READER", "20", "قارئ", "Reader")
                ),
                List.of(
                        new LanguageMetadataDto("ar", "العربية", "Arabic"),
                        new LanguageMetadataDto("en", "الإنجليزية", "English")
                ),
                List.of(
                        new AgeCategoryDto("CHILDREN", 3, 8, "أطفال (3-8 سنوات)", "Children (3-8 years)")
                ),
                List.of(
                        new StoryGenreDto("ADVENTURE", "مغامرة", "Adventure")
                ),
                List.of(
                        new VisualStyleDto("CINEMATIC_STORYBOOK", "سينمائي قصصي", "Cinematic Storybook")
                ),
                List.of(
                        new StoryLensDto("POLITICAL", "سياسي", "Political")
                ),
                List.of(
                        new AiAudienceProfileDto("8-10", "KIDS_8_10_ADVENTURE", 8, 10, "أطفال (8-10 سنوات)", "Kids (8-10)")
                ),
                new StoryPathSpecificationDto(3, 15, 5, "3 مشاهد", "15 مشاهد", "3 scenes", "15 scenes"),
                new UploadSpecificationsDto(
                        new ImageUploadSpecDto(List.of("JPG", "PNG"), "1:1.6", 1.6, 0.25, 10485760L, 10),
                        new FileUploadSpecDto(List.of("PDF"), 104857600L, 100),
                        new ImageUploadSpecDto(List.of("PNG", "JPG"), "1:1", 1.0, 0.20, 5242880L, 5),
                        new FileUploadSpecDto(List.of("PDF"), 20971520L, 20)
                )
        );

        when(metadataService.getAllMetadata()).thenReturn(dto);

        mockMvc.perform(get("/enums").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roles[0].role").value("AUTHOR"))
                .andExpect(jsonPath("$.data.roles[1].role").value("READER"))
                .andExpect(jsonPath("$.data.roles.length()").value(2))
                .andExpect(jsonPath("$.data.languages[0].code").value("ar"))
                .andExpect(jsonPath("$.data.bookAgeCategories[0].key").value("CHILDREN"))
                .andExpect(jsonPath("$.data.storyGenres[0].key").value("ADVENTURE"))
                .andExpect(jsonPath("$.data.storyVisualStyles[0].key").value("CINEMATIC_STORYBOOK"))
                .andExpect(jsonPath("$.data.uploadSpecifications.bookCover.aspectRatio").value("1:1.6"))
                .andExpect(jsonPath("$.data.uploadSpecifications.storyCover.aspectRatio").value("1:1"));
    }

    @Test
    @DisplayName("getRoles_validRequest_returnsOnlyAuthorAndReader")
    void getRoles_validRequest_returnsOnlyAuthorAndReader() throws Exception {
        when(metadataService.getRoles()).thenReturn(List.of(
                new RoleMetadataDto("AUTHOR", "10", "مؤلف", "Author"),
                new RoleMetadataDto("READER", "20", "قارئ", "Reader")
        ));

        mockMvc.perform(get("/enums/roles").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("AUTHOR"))
                .andExpect(jsonPath("$.data[1].role").value("READER"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("getLanguages_validRequest_returnsLanguagesList")
    void getLanguages_validRequest_returnsLanguagesList() throws Exception {
        when(metadataService.getLanguages()).thenReturn(List.of(
                new LanguageMetadataDto("ar", "العربية", "Arabic"),
                new LanguageMetadataDto("en", "الإنجليزية", "English")
        ));

        mockMvc.perform(get("/enums/languages").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("ar"));
    }

    @Test
    @DisplayName("getBookAgeCategories_validRequest_returnsAgeCategories")
    void getBookAgeCategories_validRequest_returnsAgeCategories() throws Exception {
        when(metadataService.getBookAgeCategories()).thenReturn(List.of(
                new AgeCategoryDto("CHILDREN", 3, 8, "أطفال (3-8 سنوات)", "Children (3-8 years)")
        ));

        mockMvc.perform(get("/enums/ages").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].key").value("CHILDREN"))
                .andExpect(jsonPath("$.data[0].minAge").value(3))
                .andExpect(jsonPath("$.data[0].maxAge").value(8));
    }

    @Test
    @DisplayName("getStoryGenres_validRequest_returnsStoryGenres")
    void getStoryGenres_validRequest_returnsStoryGenres() throws Exception {
        when(metadataService.getStoryGenres()).thenReturn(List.of(
                new StoryGenreDto("ADVENTURE", "مغامرة", "Adventure"),
                new StoryGenreDto("FANTASY", "خيال", "Fantasy")
        ));

        mockMvc.perform(get("/enums/story-genres").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].key").value("ADVENTURE"))
                .andExpect(jsonPath("$.data[0].labelAr").value("مغامرة"));
    }

    @Test
    @DisplayName("getUploadSpecifications_validRequest_returnsUploadSpecs")
    void getUploadSpecifications_validRequest_returnsUploadSpecs() throws Exception {
        when(metadataService.getUploadSpecifications()).thenReturn(new UploadSpecificationsDto(
                new ImageUploadSpecDto(List.of("JPG", "PNG", "WEBP"), "1:1.6", 1.6, 0.25, 10485760L, 10),
                new FileUploadSpecDto(List.of("PDF"), 104857600L, 100),
                new ImageUploadSpecDto(List.of("PNG", "JPG", "WEBP"), "1:1", 1.0, 0.20, 5242880L, 5),
                new FileUploadSpecDto(List.of("PDF"), 20971520L, 20)
        ));

        mockMvc.perform(get("/enums/upload-specs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookCover.aspectRatio").value("1:1.6"))
                .andExpect(jsonPath("$.data.bookCover.maxSizeMb").value(10))
                .andExpect(jsonPath("$.data.storyCover.aspectRatio").value("1:1"))
                .andExpect(jsonPath("$.data.storyCover.maxSizeMb").value(5));
    }
}
