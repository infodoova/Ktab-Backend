package com.doova.ktab.features.trailer.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;

@lombok.extern.slf4j.Slf4j
@Component
public class TrailerStore {

    private final S3Client s3;
    private final String bucket;

    public TrailerStore(S3Client s3, @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public static String key(long bookId, long trailerId, String filename) {
        return "trailers/" + bookId + "/" + trailerId + "/" + filename;
    }

    public Path downloadTo(String key, Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), target);
        return target;
    }

    public void upload(String key, Path file, String contentType) {
        long size = 0;
        try {
            size = Files.size(file);
        } catch (java.io.IOException ignored) { }
        log.info("Uploading {} ({}) to R2...", key, String.format("%.2f MB", size / (1024.0 * 1024.0)));
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .overrideConfiguration(b -> b
                                .apiCallTimeout(java.time.Duration.ofMinutes(10))
                                .apiCallAttemptTimeout(java.time.Duration.ofMinutes(5)))
                        .build(),
                RequestBody.fromFile(file));
        log.info("Uploaded {} successfully", key);
    }
}
