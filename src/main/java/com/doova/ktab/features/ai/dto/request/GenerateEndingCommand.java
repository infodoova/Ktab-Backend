package com.doova.ktab.features.ai.dto.request;

import com.doova.ktab.features.ai.enums.TargetAudienceProfile;
import com.doova.ktab.features.ai.enums.IntendedAudience;
import org.springframework.core.io.Resource;

public record GenerateEndingCommand(int approxWordCountForEnding,// Added field
                                    TargetAudienceProfile audienceProfile, Resource pdfResource) {
}
