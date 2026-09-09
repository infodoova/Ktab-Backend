package com.doova.ktab.features.tts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "elevenlabs")
public class ElevenLabsProperties {
    private String apiKey;
    private String baseUrl;
    private String voiceId;
    private String modelId;
    private String outputFormat;

    /**
     * Prefer this model for /stream/with-timestamps (try first).
     * Set to "eleven_v3" to attempt v3 alignment+audio when/if supported.
     */
    private String timestampsPreferredModelId;

    /**
     * Fallback model for /stream/with-timestamps when preferred model is rejected (4xx validation).
     * Recommended default: "eleven_multilingual_v2".
     */
    private String timestampsFallbackModelId;
}