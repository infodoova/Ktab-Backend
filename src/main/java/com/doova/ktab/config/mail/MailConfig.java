package com.doova.ktab.config.mail;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(ZeptoMailProperties.class)
@RequiredArgsConstructor
@Slf4j
public class MailConfig {

    private final ZeptoMailProperties properties;
    private final MeterRegistry meterRegistry;

    @Bean(name = "mailTaskExecutor")
    public TaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsyncCorePoolSize());
        executor.setMaxPoolSize(properties.getAsyncMaxPoolSize());
        executor.setQueueCapacity(properties.getAsyncQueueCapacity());
        executor.setThreadNamePrefix("ktab-mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Decorate tasks with SLF4J MDC so correlationId is preserved across async boundaries
        executor.setTaskDecorator(runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        });

        executor.initialize();

        meterRegistry.gauge("mail.threadpool.active", executor, ThreadPoolTaskExecutor::getActiveCount);
        meterRegistry.gauge("mail.threadpool.pool_size", executor, ThreadPoolTaskExecutor::getPoolSize);

        log.info("Initialized mailTaskExecutor (core={}, max={}, queue={})",
                properties.getAsyncCorePoolSize(), properties.getAsyncMaxPoolSize(), properties.getAsyncQueueCapacity());

        return executor;
    }

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.getHost());
        mailSender.setPort(properties.getPort());
        mailSender.setUsername(properties.getUsername());
        mailSender.setPassword(properties.getPassword());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");

        if (properties.getPort() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            // Port 587 STARTTLS (Default for ZeptoMail)
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");

        if (properties.getFromEmail() != null && !properties.getFromEmail().isBlank()) {
            props.put("mail.smtp.from", properties.getFromEmail());
        }

        props.put("mail.smtp.connectiontimeout", String.valueOf(properties.getConnectionTimeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(properties.getReadTimeoutMs()));
        props.put("mail.smtp.writetimeout", String.valueOf(properties.getWriteTimeoutMs()));

        log.info("Configured JavaMailSender for Zoho ZeptoMail (host={}:{}, username={})",
                properties.getHost(), properties.getPort(), properties.getUsername());

        return mailSender;
    }
}
