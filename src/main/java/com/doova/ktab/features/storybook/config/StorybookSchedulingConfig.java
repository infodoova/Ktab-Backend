package com.doova.ktab.features.storybook.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables @Scheduled for the storybook worker. Ktab had no @EnableScheduling before this,
 * so this also activates features.studio.reconciler.StudioOrphanReconciler.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class StorybookSchedulingConfig {

    @Bean(name = "storybookJobExecutor")
    public ThreadPoolTaskExecutor storybookJobExecutor(StorybookProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int n = properties.getWorker().getConcurrency();
        executor.setCorePoolSize(n);
        executor.setMaxPoolSize(n);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("storybook-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
