package com.doova.ktab.features.trailer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(TrailerProperties.class)
public class TrailerConfig {

    /** Scheduling only with the feature on, so a disabled feature starts no worker. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
    static class Scheduling {
    }
}
