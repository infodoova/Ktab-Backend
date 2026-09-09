package com.doova.ktab.features.tts.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(ElevenLabsProperties.class)
@Configuration
public class TtsConfig {
}
