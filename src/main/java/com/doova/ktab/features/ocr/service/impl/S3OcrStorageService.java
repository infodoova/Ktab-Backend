package com.doova.ktab.features.ocr.service.impl;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.doova.ktab.features.ocr.batch.PageItem;
import com.doova.ktab.features.ocr.service.OcrStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class S3OcrStorageService implements OcrStorageService {

    private final AmazonS3 s3;

    @Value("${aws.s3.bucketName}")
    private String bucket;

    @Value("${aws.s3.pdfPrefix}")
    private String pdfPrefix;

    @Value("${aws.s3.pagesPrefix}")
    private String pagesPrefix;

    @Value("${aws.s3.deadLetterPrefix}")
    private String deadLetterPrefix;

    @Value("${ktab.ocr.presigned.expiration-minutes:30}")
    private int presignedExpirationMinutes;

    // ======================================================
    // PDF
    // ======================================================

    @Override
    public String putBookPdf(Long bookId, InputStream pdf, long size) {
        String key = "%s/%d/source.pdf".formatted(pdfPrefix, bookId);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/pdf");
        metadata.setContentLength(size);

        s3.putObject(bucket, key, pdf, metadata);
        return key;
    }

    @Override
    public InputStream getStream(String key) {
        return s3.getObject(bucket, key).getObjectContent();
    }

    // ======================================================
    // PRESIGNED URLs (NEW - for memory-efficient OCR)
    // ======================================================

    /**
     * Generate a presigned URL for reading an S3 object.
     * Used by OCR processor to avoid loading images into heap.
     */
    public String generatePresignedUrl(String key, Duration expiration) {
        Date expirationDate = new Date(System.currentTimeMillis() + expiration.toMillis());
        
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                .withMethod(HttpMethod.GET)
                .withExpiration(expirationDate);
        
        URL url = s3.generatePresignedUrl(request);
        return url.toString();
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

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("image/png");
        metadata.setContentLength(bytes.length);

        s3.putObject(
                bucket,
                key,
                new ByteArrayInputStream(bytes),
                metadata
        );
    }

    @Override
    public List<String> listPageKeys(Long bookId) {
        String prefix = "%s/%d/pages/".formatted(pagesPrefix, bookId);
        List<String> keys = new ArrayList<>();

        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucket)
                .withPrefix(prefix);

        ListObjectsV2Result result;

        do {
            result = s3.listObjectsV2(request);

            for (S3ObjectSummary summary : result.getObjectSummaries()) {
                String key = summary.getKey();
                if (isImageKey(key)) {
                    keys.add(key);
                }
            }

            request.setContinuationToken(result.getNextContinuationToken());
        }
        while (result.isTruncated());

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
     * Get page bytes directly from S3 (use sparingly - prefer presigned URLs).
     */
    public byte[] getPageBytes(String key) {
        try (InputStream in = s3.getObject(bucket, key).getObjectContent()) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read page from S3: " + key, e);
        }
    }

    // ======================================================
    // DEAD LETTER
    // ======================================================

    @Override
    public void moveToDeadLetter(String sourceKey, Long bookId, int page, String reason) {
        String targetKey = "%s/%d/page-%04d.png".formatted(deadLetterPrefix, bookId, page);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setUserMetadata(
                Map.of("dlq-reason", sanitize(reason))
        );

        CopyObjectRequest copyRequest =
                new CopyObjectRequest(bucket, sourceKey, bucket, targetKey)
                        .withNewObjectMetadata(metadata);

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

    private String resolveMime(ObjectMetadata meta, String key) {
        if (meta != null && meta.getContentType() != null) {
            return meta.getContentType();
        }
        return resolveMimeFromKey(key);
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
            ListObjectsV2Request request = new ListObjectsV2Request()
                    .withBucketName(bucket)
                    .withPrefix(prefix)
                    .withContinuationToken(continuationToken);

            ListObjectsV2Result result = s3.listObjectsV2(request);

            List<DeleteObjectsRequest.KeyVersion> keys =
                    result.getObjectSummaries()
                            .stream()
                            .map(o -> new DeleteObjectsRequest.KeyVersion(o.getKey()))
                            .collect(Collectors.toList());

            if (!keys.isEmpty()) {
                s3.deleteObjects(
                        new DeleteObjectsRequest(bucket).withKeys(keys)
                );
            }

            continuationToken = result.getNextContinuationToken();
        }
        while (continuationToken != null);
    }

    private String sanitize(String s) {
        return (s == null || s.isBlank())
                ? "unknown"
                : s.substring(0, Math.min(180, s.length()));
    }
}
