package com.doova.ktab.service.metadata.impl;

import com.doova.ktab.config.image.ImageValidationProperties;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto;
import com.doova.ktab.enums.book.AppLanguage;
import com.doova.ktab.enums.book.BookAgeCategory;
import com.doova.ktab.features.story.enums.StoryGenre;
import com.doova.ktab.features.story.enums.StoryLens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataServiceImplTest {

    private ImageValidationProperties properties;
    private MetadataServiceImpl metadataService;

    @BeforeEach
    void setUp() {
        properties = new ImageValidationProperties();
        properties.setCoverRatio(1.6);
        properties.setCoverTolerance(0.25);
        properties.setSquareRatio(1.0);
        properties.setSquareTolerance(0.20);
        metadataService = new MetadataServiceImpl(properties);
    }

    @Test
    @DisplayName("getRoles_rolesRequested_returnsOnlyAuthorAndReader")
    void getRoles_rolesRequested_returnsOnlyAuthorAndReader() {
        var roles = metadataService.getRoles();

        assertThat(roles).hasSize(2);
        assertThat(roles).extracting(AppEnumsResponseDto.RoleMetadataDto::role)
                .containsExactly("AUTHOR", "READER");
        assertThat(roles).extracting(AppEnumsResponseDto.RoleMetadataDto::code)
                .containsExactly("10", "20");
        assertThat(roles).noneMatch(r -> r.role().equalsIgnoreCase("ADMIN"));
        assertThat(roles).noneMatch(r -> r.role().equalsIgnoreCase("LIBRARIAN"));
        assertThat(roles).noneMatch(r -> r.role().equalsIgnoreCase("ADMIN_LIBRARIAN"));
    }

    @Test
    @DisplayName("getLanguages_languagesRequested_containsSupportedLanguages")
    void getLanguages_languagesRequested_containsSupportedLanguages() {
        var languages = metadataService.getLanguages();

        assertThat(languages).hasSize(AppLanguage.values().length);
        assertThat(languages).extracting(AppEnumsResponseDto.LanguageMetadataDto::code)
                .contains("ar", "en", "fr", "es", "de");
    }

    @Test
    @DisplayName("getBookAgeCategories_categoriesRequested_matchesExpectedRangesAndLabels")
    void getBookAgeCategories_categoriesRequested_matchesExpectedRangesAndLabels() {
        var ageCategories = metadataService.getBookAgeCategories();

        assertThat(ageCategories).hasSize(BookAgeCategory.values().length);
        assertThat(ageCategories).extracting(AppEnumsResponseDto.AgeCategoryDto::key)
                .containsExactly("CHILDREN", "EARLY_TEENS", "YOUTH", "ADULTS");

        var children = ageCategories.get(0);
        assertThat(children.minAge()).isEqualTo(3);
        assertThat(children.maxAge()).isEqualTo(8);
        assertThat(children.labelAr()).isEqualTo("أطفال (3-8 سنوات)");

        var adults = ageCategories.get(3);
        assertThat(adults.minAge()).isEqualTo(25);
        assertThat(adults.maxAge()).isNull();
        assertThat(adults.labelAr()).isEqualTo("كبار (+25)");
    }

    @Test
    @DisplayName("getStoryGenres_genresRequested_matchesSixUiGenres")
    void getStoryGenres_genresRequested_matchesSixUiGenres() {
        var genres = metadataService.getStoryGenres();

        assertThat(genres).hasSize(6);
        assertThat(genres).extracting(AppEnumsResponseDto.StoryGenreDto::key)
                .containsExactly("ADVENTURE", "FANTASY", "MYSTERY", "SCI_FI", "HORROR", "DRAMA");
        assertThat(genres).extracting(AppEnumsResponseDto.StoryGenreDto::labelAr)
                .containsExactly("مغامرة", "خيال", "غموض", "خيال علمي", "رعب", "دراما");
    }

    @Test
    @DisplayName("getStoryVisualStyles_stylesRequested_matchesUiVisualStyles")
    void getStoryVisualStyles_stylesRequested_matchesUiVisualStyles() {
        var styles = metadataService.getStoryVisualStyles();

        assertThat(styles).hasSize(6);
        assertThat(styles).extracting(AppEnumsResponseDto.VisualStyleDto::key)
                .containsExactly(
                        "CINEMATIC_STORYBOOK",
                        "MODERN_DIGITAL_ART",
                        "DARK_GRAPHIC_NOVEL",
                        "ANIME",
                        "WATERCOLOR",
                        "CLASSIC_OIL_PAINTING"
                );
        assertThat(styles).extracting(AppEnumsResponseDto.VisualStyleDto::labelAr)
                .containsExactly(
                        "سينمائي قصصي",
                        "فن رقمي عصري",
                        "رواية مصورة مظلمة",
                        "أنمي ورسوم متحركة",
                        "ألوان مائية فنية",
                        "رسم زيتي كلاسيكي"
                );
    }

    @Test
    @DisplayName("getStoryLenses_lensesRequested_matchesStoryLenses")
    void getStoryLenses_lensesRequested_matchesStoryLenses() {
        var lenses = metadataService.getStoryLenses();

        assertThat(lenses).hasSize(StoryLens.values().length);
        assertThat(lenses).extracting(AppEnumsResponseDto.StoryLensDto::key)
                .contains("POLITICAL", "PSYCHOLOGICAL", "SURVIVAL", "MORAL");
    }

    @Test
    @DisplayName("getAiAudienceProfiles_profilesRequested_matchesEndingGeneratorOptions")
    void getAiAudienceProfiles_profilesRequested_matchesEndingGeneratorOptions() {
        var profiles = metadataService.getAiAudienceProfiles();

        assertThat(profiles).hasSize(4);
        assertThat(profiles).extracting(AppEnumsResponseDto.AiAudienceProfileDto::key)
                .containsExactly("8-10", "13-16", "16-24", "25+");
        assertThat(profiles.get(0).labelAr()).contains("أطفال (8-10 سنوات)");
        assertThat(profiles.get(1).labelAr()).contains("يافعين (13-16 سنة)");
        assertThat(profiles.get(2).labelAr()).contains("شباب (16-24 سنة)");
        assertThat(profiles.get(3).labelAr()).contains("عام وكبار (+25 سنة)");
    }

    @Test
    @DisplayName("getStoryPathSpecifications_specsRequested_returnsSliderConfig")
    void getStoryPathSpecifications_specsRequested_returnsSliderConfig() {
        var specs = metadataService.getStoryPathSpecifications();

        assertThat(specs.minScenes()).isEqualTo(3);
        assertThat(specs.maxScenes()).isEqualTo(15);
        assertThat(specs.defaultScenes()).isEqualTo(5);
        assertThat(specs.minLabelAr()).isEqualTo("3 مشاهد (سريعة)");
        assertThat(specs.maxLabelAr()).isEqualTo("15 مشاهد (ملحمية)");
    }

    @Test
    @DisplayName("getUploadSpecifications_specsRequested_returnsTolerancesAndLimits")
    void getUploadSpecifications_specsRequested_returnsTolerancesAndLimits() {
        var uploadSpecs = metadataService.getUploadSpecifications();

        assertThat(uploadSpecs.bookCover().aspectRatio()).isEqualTo("1:1.6");
        assertThat(uploadSpecs.bookCover().targetRatio()).isEqualTo(1.6);
        assertThat(uploadSpecs.bookCover().tolerance()).isEqualTo(0.25);
        assertThat(uploadSpecs.bookCover().maxSizeMb()).isEqualTo(10);

        assertThat(uploadSpecs.bookPdf().allowedFormats()).containsExactly("PDF");
        assertThat(uploadSpecs.bookPdf().maxSizeMb()).isEqualTo(100);

        assertThat(uploadSpecs.storyCover().aspectRatio()).isEqualTo("1:1");
        assertThat(uploadSpecs.storyCover().targetRatio()).isEqualTo(1.0);
        assertThat(uploadSpecs.storyCover().tolerance()).isEqualTo(0.20);
        assertThat(uploadSpecs.storyCover().maxSizeMb()).isEqualTo(5);

        assertThat(uploadSpecs.aiPdfDraft().allowedFormats()).containsExactly("PDF");
        assertThat(uploadSpecs.aiPdfDraft().maxSizeMb()).isEqualTo(20);
    }

    @Test
    @DisplayName("getAllMetadata_allEnumsRequested_returnsCompleteDto")
    void getAllMetadata_allEnumsRequested_returnsCompleteDto() {
        var all = metadataService.getAllMetadata();

        assertThat(all.roles()).hasSize(2);
        assertThat(all.languages()).isNotEmpty();
        assertThat(all.bookAgeCategories()).hasSize(4);
        assertThat(all.storyGenres()).hasSize(6);
        assertThat(all.storyVisualStyles()).hasSize(6);
        assertThat(all.storyLenses()).hasSize(4);
        assertThat(all.aiAudienceProfiles()).hasSize(4);
        assertThat(all.storyPathSpecifications()).isNotNull();
        assertThat(all.uploadSpecifications()).isNotNull();
    }
}
