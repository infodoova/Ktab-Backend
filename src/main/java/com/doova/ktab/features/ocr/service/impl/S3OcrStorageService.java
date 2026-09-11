package com.doova.ktab.features.ocr.service.impl;

import com.doova.ktab.features.ocr.batch.PageItem;
import com.doova.ktab.features.ocr.service.OcrStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class S3OcrStorageService implements OcrStorageService {

    private final S3Client s3;
    private final S3Presigner s3Presigner;

    @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName}}")
    private String bucket;

    @Value("${cloudflare.r2.pdfPrefix:${aws.s3.pdfPrefix:books}}")
    private String pdfPrefix;

    @Value("${cloudflare.r2.pagesPrefix:${aws.s3.pagesPrefix:books}}")
    private String pagesPrefix;

    @Value("${cloudflare.r2.deadLetterPrefix:${aws.s3.deadLetterPrefix:dead-letter/books}}")
    private String deadLetterPrefix;

    @Value("${ktab.ocr.presigned.expiration-minutes:30}")
    private int presignedExpirationMinutes;

    // ======================================================
    // PDF
    // ======================================================

    @Override
    public String putBookPdf(Long bookId, InputStream pdf, long size) {
        String key = "%s/%d/source.pdf".formatted(pdfPrefix, bookId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/pdf")
                .contentLength(size)
                .build();

        s3.putObject(request, RequestBody.fromInputStream(pdf, size));
        return key;
    }

    @Override
    public InputStream getStream(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        return s3.getObject(request);
    }

    // ======================================================
    // PRESIGNED URLs (NEW - for memory-efficient OCR)
    // ======================================================

    /**
     * Generate a presigned URL for reading an S3/R2 object.
     * Used by OCR processor to avoid loading images into heap.
     */
    public String generatePresignedUrl(String key, Duration expiration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Generate presigned URL with default expiration.
     */
    public String generatePresignedUrl(String key) {
        return generatePresignedUrl(key, Duration.ofMinutes(presignedExpirationMinutes));
    }

    // ======================================================
    // PAGE IMAGES
    // ======================================================

    @Override
    public void uploadPagePng(Long bookId, int page, byte[] bytes) {
        String key = "%s/%d/pages/page-%04d.png".formatted(pagesPrefix, bookId, page);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("image/png")
                .contentLength((long) bytes.length)
                .build();

        s3.putObject(request, RequestBody.fromBytes(bytes));
    }

    @Override
    public List<String> listPageKeys(Long bookId) {
        String prefix = "%s/%d/pages/".formatted(pagesPrefix, bookId);
        List<String> keys = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix);
            if (continuationToken != null) {
                reqBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response result = s3.listObjectsV2(reqBuilder.build());

            for (S3Object summary : result.contents()) {
                String key = summary.key();
                if (isImageKey(key)) {
                    keys.add(key);
                }
            }

            continuationToken = result.isTruncated() ? result.nextContinuationToken() : null;
        } while (continuationToken != null);

        // Deterministic order: page-0001.png → page-0034.png
        keys.sort(String::compareTo);
        return keys;
    }

    // ======================================================
    // PAGE ITEMS WITH PRESIGNED URLs (OPTIMIZED)
    // ======================================================

    @Override
    public List<PageItem> listPages(Long bookId) {
        List<String> keys = listPageKeys(bookId);
        List<PageItem> pages = new ArrayList<>(keys.size());

        for (String key : keys) {
            String mime = resolveMimeFromKey(key);
            String presignedUrl = generatePresignedUrl(key);

            pages.add(new PageItem(
                    bookId,
                    extractPageNumber(key),
                    key,
                    mime,
                    presignedUrl
            ));
        }

        return pages;
    }

    /**
     * Get page bytes directly from S3/R2 (use sparingly - prefer presigned URLs).
     */
    public byte[] getPageBytes(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        try (InputStream in = s3.getObject(request)) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read page from S3/R2: " + key, e);
        }
    }

    // ======================================================
    // DEAD LETTER
    // ======================================================

    @Override
    public void moveToDeadLetter(String sourceKey, Long bookId, int page, String reason) {
        String targetKey = "%s/%d/page-%04d.png".formatted(deadLetterPrefix, bookId, page);

        String encodedSource = URLEncoder.encode(bucket + "/" + sourceKey, StandardCharsets.UTF_8).replace("+", "%20");

        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .copySource(encodedSource)
                .destinationBucket(bucket)
                .destinationKey(targetKey)
                .metadata(Map.of("dlq-reason", sanitize(reason)))
                .metadataDirective(MetadataDirective.REPLACE)
                .build();

        s3.copyObject(copyRequest);
    }

    // ======================================================
    // CLEANUP
    // ======================================================

    @Override
    public void deletePages(Long bookId) {
        deletePrefix("%s/%d/pages/".formatted(pagesPrefix, bookId));
    }

    // ======================================================
    // INTERNAL HELPERS
    // ======================================================

    private boolean isImageKey(String key) {
        String lower = key.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg");
    }

    private String resolveMimeFromKey(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private int extractPageNumber(String key) {
        // Example: books/10/pages/page-0007.png
        int dash = key.lastIndexOf('-');
        int dot = key.lastIndexOf('.');

        if (dash < 0 || dot < 0 || dash >= dot) {
            throw new IllegalStateException("Invalid page key format: " + key);
        }

        return Integer.parseInt(key.substring(dash + 1, dot));
    }

    private void deletePrefix(String prefix) {
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix);
            if (continuationToken != null) {
                reqBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response result = s3.listObjectsV2(reqBuilder.build());

            List<ObjectIdentifier> keys = result.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                    .toList();

            if (!keys.isEmpty()) {
                s3.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(keys).build())
                        .build());
            }

            continuationToken = result.isTruncated() ? result.nextContinuationToken() : null;
        } while (continuationToken != null);
    }

    private String sanitize(String s) {
        return (s == null || s.isBlank())
                ? "unknown"
                : s.substring(0, Math.min(180, s.length()));
    }
}

