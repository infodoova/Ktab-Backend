package com.doova.ktab.service.metadata;

import com.doova.ktab.dto.metadata.AppEnumsResponseDto;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.*;

import java.util.List;

public interface MetadataService {

    AppEnumsResponseDto getAllMetadata();

    List<RoleMetadataDto> getRoles();

    List<LanguageMetadataDto> getLanguages();

    List<AgeCategoryDto> getBookAgeCategories();

    List<StoryGenreDto> getStoryGenres();

    List<VisualStyleDto> getStoryVisualStyles();

    List<StoryLensDto> getStoryLenses();

    List<AiAudienceProfileDto> getAiAudienceProfiles();

    StoryPathSpecificationDto getStoryPathSpecifications();

    UploadSpecificationsDto getUploadSpecifications();
}
