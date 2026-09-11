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
        return URI.create("https://s3." + region + ".amazonaws.com");
    }

    private StaticCredentialsProvider getCredentialsProvider() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        return StaticCredentialsProvider.create(credentials);
    }

    private Region getRegion() {
        return (region == null || region.isBlank() || "auto".equalsIgnoreCase(region))
                ? Region.of("auto")
                : Region.of(region);
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(resolveEndpoint())
                .credentialsProvider(getCredentialsProvider())
                .region(getRegion())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(resolveEndpoint())
                .credentialsProvider(getCredentialsProvider())
                .region(getRegion())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}