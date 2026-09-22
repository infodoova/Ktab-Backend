package com.doova.ktab.service.metadata.impl;

import com.doova.ktab.config.image.ImageValidationProperties;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.*;
import com.doova.ktab.enums.book.AppLanguage;
import com.doova.ktab.enums.book.BookAgeCategory;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.features.ai.enums.TargetAudienceProfile;
import com.doova.ktab.features.story.enums.StoryGenre;
import com.doova.ktab.features.story.enums.StoryLens;
import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.service.metadata.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetadataServiceImpl implements MetadataService {

    private final ImageValidationProperties imageValidationProperties;

    @Override
    public AppEnumsResponseDto getAllMetadata() {
        return new AppEnumsResponseDto(
                getRoles(),
                getLanguages(),
                getBookAgeCategories(),
                getStoryGenres(),
                getStoryVisualStyles(),
                getStoryLenses(),
                getAiAudienceProfiles(),
                getStoryPathSpecifications(),
                getUploadSpecifications()
        );
    }

    @Override
    public List<RoleMetadataDto> getRoles() {
        // Explicitly restricted to AUTHOR and READER only per requirements
        return List.of(
                new RoleMetadataDto(UserRole.AUTHOR.name(), UserRole.AUTHOR.getCode(), "مؤلف", "Author"),
                new RoleMetadataDto(UserRole.READER.name(), UserRole.READER.getCode(), "قارئ", "Reader")
        );
    }

    @Override
    public List<LanguageMetadataDto> getLanguages() {
        return Arrays.stream(AppLanguage.values())
                .map(lang -> new LanguageMetadataDto(lang.getCode(), lang.getLabelAr(), lang.getLabelEn()))
                .toList();
    }

    @Override
    public List<AgeCategoryDto> getBookAgeCategories() {
        return Arrays.stream(BookAgeCategory.values())
                .map(cat -> new AgeCategoryDto(cat.name(), cat.getMinAge(), cat.getMaxAge(), cat.getLabelAr(), cat.getLabelEn()))
                .toList();
    }

    @Override
    public List<StoryGenreDto> getStoryGenres() {
        return Arrays.stream(StoryGenre.values())
                .map(genre -> new StoryGenreDto(genre.name(), genre.getLabelAr(), genre.getLabelEn()))
                .toList();
    }

    @Override
    public List<VisualStyleDto> getStoryVisualStyles() {
        // Return active visual styles presented in story creation UI
        return List.of(
                new VisualStyleDto(StoryVisualStyle.CINEMATIC_STORYBOOK.name(), StoryVisualStyle.CINEMATIC_STORYBOOK.getLabelAr(), StoryVisualStyle.CINEMATIC_STORYBOOK.getLabelEn()),
                new VisualStyleDto(StoryVisualStyle.MODERN_DIGITAL_ART.name(), StoryVisualStyle.MODERN_DIGITAL_ART.getLabelAr(), StoryVisualStyle.MODERN_DIGITAL_ART.getLabelEn()),
                new VisualStyleDto(StoryVisualStyle.DARK_GRAPHIC_NOVEL.name(), StoryVisualStyle.DARK_GRAPHIC_NOVEL.getLabelAr(), StoryVisualStyle.DARK_GRAPHIC_NOVEL.getLabelEn()),
                new VisualStyleDto(StoryVisualStyle.ANIME.name(), StoryVisualStyle.ANIME.getLabelAr(), StoryVisualStyle.ANIME.getLabelEn()),
                new VisualStyleDto(StoryVisualStyle.WATERCOLOR.name(), StoryVisualStyle.WATERCOLOR.getLabelAr(), StoryVisualStyle.WATERCOLOR.getLabelEn()),
                new VisualStyleDto(StoryVisualStyle.CLASSIC_OIL_PAINTING.name(), StoryVisualStyle.CLASSIC_OIL_PAINTING.getLabelAr(), StoryVisualStyle.CLASSIC_OIL_PAINTING.getLabelEn())
        );
    }

    @Override
    public List<StoryLensDto> getStoryLenses() {
        return List.of(
                new StoryLensDto(StoryLens.POLITICAL.name(), "سياسي", "Political"),
                new StoryLensDto(StoryLens.PSYCHOLOGICAL.name(), "نفسي", "Psychological"),
                new StoryLensDto(StoryLens.SURVIVAL.name(), "صراع البقاء", "Survival"),
                new StoryLensDto(StoryLens.MORAL.name(), "أخلاقي", "Moral")
        );
    }

    @Override
    public List<AiAudienceProfileDto> getAiAudienceProfiles() {
        return List.of(
                new AiAudienceProfileDto(
                        TargetAudienceProfile.KIDS_8_10_ADVENTURE.getAgeRangeKey(),
                        TargetAudienceProfile.KIDS_8_10_ADVENTURE.name(),
                        8, 10,
                        TargetAudienceProfile.KIDS_8_10_ADVENTURE.getLabelAr(),
                        TargetAudienceProfile.KIDS_8_10_ADVENTURE.getLabelEn()
                ),
                new AiAudienceProfileDto(
                        TargetAudienceProfile.TEENS_13_16_DYSTOPIAN.getAgeRangeKey(),
                        TargetAudienceProfile.TEENS_13_16_DYSTOPIAN.name(),
                        13, 16,
                        TargetAudienceProfile.TEENS_13_16_DYSTOPIAN.getLabelAr(),
                        TargetAudienceProfile.TEENS_13_16_DYSTOPIAN.getLabelEn()
                ),
                new AiAudienceProfileDto(
                        TargetAudienceProfile.YOUTH_16_24_FANTASY.getAgeRangeKey(),
                        TargetAudienceProfile.YOUTH_16_24_FANTASY.name(),
                        16, 24,
                        TargetAudienceProfile.YOUTH_16_24_FANTASY.getLabelAr(),
                        TargetAudienceProfile.YOUTH_16_24_FANTASY.getLabelEn()
                ),
                new AiAudienceProfileDto(
                        TargetAudienceProfile.ADULTS_25_PLUS_DRAMA.getAgeRangeKey(),
                        TargetAudienceProfile.ADULTS_25_PLUS_DRAMA.name(),
                        25, null,
                        TargetAudienceProfile.ADULTS_25_PLUS_DRAMA.getLabelAr(),
                        TargetAudienceProfile.ADULTS_25_PLUS_DRAMA.getLabelEn()
                )
        );
    }

    @Override
    public StoryPathSpecificationDto getStoryPathSpecifications() {
        return new StoryPathSpecificationDto(
                3,
                15,
                5,
                "3 مشاهد (سريعة)",
                "15 مشاهد (ملحمية)",
                "3 scenes (quick)",
                "15 scenes (epic)"
        );
    }

    @Override
    public UploadSpecificationsDto getUploadSpecifications() {
        ImageUploadSpecDto bookCover = new ImageUploadSpecDto(
                List.of("JPG", "JPEG", "PNG", "WEBP"),
                "1:1.6",
                imageValidationProperties.getCoverRatio(),
                imageValidationProperties.getCoverTolerance(),
                10L * 1024L * 1024L,
                10
        );

        FileUploadSpecDto bookPdf = new FileUploadSpecDto(
                List.of("PDF"),
                100L * 1024L * 1024L,
                100
        );

        ImageUploadSpecDto storyCover = new ImageUploadSpecDto(
                List.of("PNG", "JPG", "JPEG", "WEBP"),
                "1:1",
                imageValidationProperties.getSquareRatio(),
                imageValidationProperties.getSquareTolerance(),
                5L * 1024L * 1024L,
                5
        );

        FileUploadSpecDto aiPdfDraft = new FileUploadSpecDto(
                List.of("PDF"),
                20L * 1024L * 1024L,
                20
        );

        return new UploadSpecificationsDto(bookCover, bookPdf, storyCover, aiPdfDraft);
    }
}
