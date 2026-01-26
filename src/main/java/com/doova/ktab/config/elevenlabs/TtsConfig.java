package com.doova.ktab.config.elevenlabs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(ElevenLabsProperties.class)
@Configuration
public class TtsConfig {
}
