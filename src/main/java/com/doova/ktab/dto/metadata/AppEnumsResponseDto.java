package com.doova.ktab.dto.metadata;

import java.util.List;

public record AppEnumsResponseDto(
        List<RoleMetadataDto> roles,
        List<LanguageMetadataDto> languages,
        List<AgeCategoryDto> bookAgeCategories,
        List<StoryGenreDto> storyGenres,
        List<VisualStyleDto> storyVisualStyles,
        List<StoryLensDto> storyLenses,
        List<AiAudienceProfileDto> aiAudienceProfiles,
        StoryPathSpecificationDto storyPathSpecifications,
        UploadSpecificationsDto uploadSpecifications
) {

    public record RoleMetadataDto(
            String role,
            String code,
            String labelAr,
            String labelEn
    ) {}

    public record LanguageMetadataDto(
            String code,
            String labelAr,
            String labelEn
    ) {}

    public record AgeCategoryDto(
            String key,
            Integer minAge,
            Integer maxAge,
            String labelAr,
            String labelEn
    ) {}

    public record StoryGenreDto(
            String key,
            String labelAr,
            String labelEn
    ) {}

    public record VisualStyleDto(
            String key,
            String labelAr,
            String labelEn
    ) {}

    public record StoryLensDto(
            String key,
            String labelAr,
            String labelEn
    ) {}

    public record AiAudienceProfileDto(
            String key,
            String name,
            Integer minAge,
            Integer maxAge,
            String labelAr,
            String labelEn
    ) {}

    public record StoryPathSpecificationDto(
            int minScenes,
            int maxScenes,
            int defaultScenes,
            String minLabelAr,
            String maxLabelAr,
            String minLabelEn,
            String maxLabelEn
    ) {}

    public record UploadSpecificationsDto(
            ImageUploadSpecDto bookCover,
            FileUploadSpecDto bookPdf,
            ImageUploadSpecDto storyCover,
            FileUploadSpecDto aiPdfDraft
    ) {}

    public record ImageUploadSpecDto(
            List<String> allowedFormats,
            String aspectRatio,
            Double targetRatio,
            Double tolerance,
            long maxSizeBytes,
            int maxSizeMb
    ) {}

    public record FileUploadSpecDto(
            List<String> allowedFormats,
            long maxSizeBytes,
            int maxSizeMb
    ) {}
}
