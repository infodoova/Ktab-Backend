package com.doova.ktab.controller.v1.metadata;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.*;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.metadata.MetadataService;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/enums", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Enum & Metadata API", description = "Endpoints providing system enums, dropdown options, and form specifications.")
public class EnumMetadataController {

    private final MetadataService metadataService;
    private final MessageSource messageSource;

    @Operation(summary = "Get all application enums and upload specifications")
    @GetMapping("")
    public ResponseEntity<ApiResponse<AppEnumsResponseDto>> getAllEnums() {
        AppEnumsResponseDto response = metadataService.getAllMetadata();
        return ResponseUtils.success(response, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    @Operation(summary = "Get supported languages")
    @GetMapping("/languages")
    public ResponseEntity<ApiResponse<List<LanguageMetadataDto>>> getLanguages() {
        List<LanguageMetadataDto> languages = metadataService.getLanguages();
        return ResponseUtils.success(languages, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    @Operation(summary = "Get book reader age categories")
    @GetMapping("/ages")
    public ResponseEntity<ApiResponse<List<AgeCategoryDto>>> getBookAgeCategories() {
        List<AgeCategoryDto> ages = metadataService.getBookAgeCategories();
        return ResponseUtils.success(ages, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get interactive story genres")
    @GetMapping("/story-genres")
    public ResponseEntity<ApiResponse<List<StoryGenreDto>>> getStoryGenres() {
        List<StoryGenreDto> genres = metadataService.getStoryGenres();
        return ResponseUtils.success(genres, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get interactive story visual styles")
    @GetMapping("/visual-styles")
    public ResponseEntity<ApiResponse<List<VisualStyleDto>>> getStoryVisualStyles() {
        List<VisualStyleDto> styles = metadataService.getStoryVisualStyles();
        return ResponseUtils.success(styles, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get interactive story lenses")
    @GetMapping("/story-lenses")
    public ResponseEntity<ApiResponse<List<StoryLensDto>>> getStoryLenses() {
        List<StoryLensDto> lenses = metadataService.getStoryLenses();
        return ResponseUtils.success(lenses, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get AI book ending target audience profiles")
    @GetMapping("/ai-audience-profiles")
    public ResponseEntity<ApiResponse<List<AiAudienceProfileDto>>> getAiAudienceProfiles() {
        List<AiAudienceProfileDto> profiles = metadataService.getAiAudienceProfiles();
        return ResponseUtils.success(profiles, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    @Operation(summary = "Get file and image upload specifications")
    @GetMapping("/upload-specs")
    public ResponseEntity<ApiResponse<UploadSpecificationsDto>> getUploadSpecifications() {
        UploadSpecificationsDto specs = metadataService.getUploadSpecifications();
        return ResponseUtils.success(specs, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
