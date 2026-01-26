package com.doova.ktab.ai.dto.request;

import com.doova.ktab.ai.enums.TargetAudienceProfile;
import com.doova.ktab.ai.enums.IntendedAudience;
import org.springframework.core.io.Resource;

public record GenerateEndingCommand(int approxWordCountForEnding,// Added field
                                    TargetAudienceProfile audienceProfile, Resource pdfResource) {
}
