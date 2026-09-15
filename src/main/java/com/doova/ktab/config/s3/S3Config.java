package com.doova.ktab.config.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@lombok.extern.slf4j.Slf4j
public class S3Config {

    @Value("${cloudflare.r2.accountId:${aws.s3.accountId:}}")
    private String accountId;

    @Value("${cloudflare.r2.accessKeyId:${aws.s3.accessKeyId:}}")
    private String accessKey;

    @Value("${cloudflare.r2.secretKey:${aws.s3.secretKey:}}")
    private String secretKey;

    @Value("${cloudflare.r2.endpoint:}")
    private String customEndpoint;

    @Value("${cloudflare.r2.region:${aws.s3.region:auto}}")
    private String region;

    private URI resolveEndpoint() {
        if (customEndpoint != null && !customEndpoint.isBlank()) {
            return URI.create(customEndpoint);
        }
        if (accountId != null && !accountId.isBlank()) {
            return URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
        }
        // Fallback to standard AWS endpoint if no account ID / custom endpoint
        String fallbackRegion = (region == null || region.isBlank() || "auto".equalsIgnoreCase(region)) ? "us-east-1" : region;
        log.warn("Neither cloudflare.r2.accountId nor cloudflare.r2.endpoint is configured! Falling back to AWS S3 endpoint with region: {}", fallbackRegion);
        return URI.create("https://s3." + fallbackRegion + ".amazonaws.com");
    }

    private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider getCredentialsProvider() {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("S3/R2 credentials are missing, using dummy credentials provider");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy-access-key", "dummy-secret-key"));
        }
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        return StaticCredentialsProvider.create(credentials);
    }

    private Region getRegion() {
        return (region == null || region.isBlank() || "auto".equalsIgnoreCase(region))
                ? Region.of("auto")
                : Region.of(region);
    }

    private software.amazon.awssdk.core.client.config.ClientOverrideConfiguration getOverrideConfiguration() {
        return software.amazon.awssdk.core.client.config.ClientOverrideConfiguration.builder()
                .apiCallTimeout(java.time.Duration.ofSeconds(30))
                .apiCallAttemptTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public S3Client s3Client() {
        URI endpoint = resolveEndpoint();
        log.info("Initialized S3Client with endpoint: {}, region: {}", endpoint, getRegion());
        return S3Client.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(getCredentialsProvider())
                .region(getRegion())
                .overrideConfiguration(getOverrideConfiguration())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        URI endpoint = resolveEndpoint();
        log.info("Initialized S3Presigner with endpoint: {}, region: {}", endpoint, getRegion());
        return S3Presigner.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(getCredentialsProvider())
                .region(getRegion())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}