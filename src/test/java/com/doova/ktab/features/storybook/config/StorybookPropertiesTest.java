package com.doova.ktab.features.storybook.config;

import com.doova.ktab.features.storybook.enums.AgeBand;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(StorybookProperties.class);

    @Test
    void defaultsMatchTheSpec() {
        runner.run(ctx -> {
            StorybookProperties p = ctx.getBean(StorybookProperties.class);
            assertThat(p.isEnabled()).isFalse();
            assertThat(p.getLlm().getModel()).isEqualTo("claude-sonnet-5");
            assertThat(p.getImage().getPrimaryModel()).isEqualTo("gemini-3.1-flash-image");
            assertThat(p.getImage().getFallbackModel()).isEqualTo("gemini-3.1-flash-lite-image");
            assertThat(p.getImage().getImageSize()).isEqualTo("1K");
            assertThat(p.getImage().getAspectRatio()).isEqualTo("1:1");
            assertThat(p.getImage().getMaxGenerations()).isEqualTo(4);
            assertThat(p.getImage().getPrimaryGenerations()).isEqualTo(2);
            assertThat(p.getPricing().getImagePerImageUsd())
                    .containsEntry("gemini-3.1-flash-image-preview", new BigDecimal("0.101"))
                    .containsEntry("gemini-3-pro-image-preview", new BigDecimal("0.134"));
            assertThat(p.getPricing().getLlm().get("claude-sonnet-5").getOutputPerMillionUsd())
                    .isEqualByComparingTo("10.00");
            assertThat(p.getWorker().getConcurrency()).isEqualTo(4);
        });
    }

    @Test
    void bindsModelKeysThatContainDots() {
        runner.withPropertyValues(
                "ktab.storybook.enabled=true",
                "ktab.storybook.pricing.image-per-image-usd[gemini-3.1-flash-image-preview]=0.050",
                "ktab.storybook.worker.poll-delay=5s"
        ).run(ctx -> {
            StorybookProperties p = ctx.getBean(StorybookProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getPricing().getImagePerImageUsd().get("gemini-3.1-flash-image-preview"))
                    .isEqualByComparingTo("0.050");
            assertThat(p.getWorker().getPollDelay()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void ageBandsCarryTheWordLimits() {
        assertThat(AgeBand.AGE_3_5.maxWordsPerPage()).isEqualTo(25);
        assertThat(AgeBand.AGE_6_8.maxWordsPerPage()).isEqualTo(45);
        assertThat(AgeBand.AGE_9_10.maxWordsPerPage()).isEqualTo(70);
    }
}
