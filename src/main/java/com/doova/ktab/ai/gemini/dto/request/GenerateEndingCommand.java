package com.doova.ktab.ai.gemini.dto.request;

import com.doova.ktab.ai.gemini.enums.TargetAudienceProfile;
import org.springframework.core.io.Resource;

public record GenerateEndingCommand(
        int approxWordCountForEnding,
        TargetAudienceProfile audienceProfile,
        Resource pdfResource
) {}
