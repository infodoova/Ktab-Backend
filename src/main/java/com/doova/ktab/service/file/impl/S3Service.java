package com.doova.ktab.service.file.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.exception.S3UploadException;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.utils.validator.ImageValidator;
import com.doova.ktab.utils.validator.PdfValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ImageValidator imageValidator;
    private final PdfValidator pdfValidator;

    @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName}}")
    private String bucketName;

    @Value("${cloudflare.r2.publicUrl:}")
    private String publicUrl;

    @Value("${cloudflare.r2.accountId:${aws.s3.accountId:}}")
    private String accountId;

    @Value("${cloudflare.r2.region:${aws.s3.region:auto}}")
    private String region;

    @Value("${cloudflare.r2.url-strategy:${aws.s3.url-strategy:SIGNED}}")
    private UrlStrategy urlStrategy;

    @Value("${cloudflare.r2.presigned.expiration-minutes:${aws.s3.presigned.expiration-minutes:10}}")
    private long expirationMinutes;

    // ======================================================
    // STORE FILE
    // ======================================================
    @Override
    public String storeFile(MultipartFile file, String directoryKey) throws IOException {

        try {
            String contentType = file.getContentType();

            boolean isImage = contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/jpg"));

            boolean isPdf = contentType != null && (contentType.equals("application/pdf") || contentType.equals("application/x-pdf") || contentType.equals("application/acrobat") || contentType.equals("applications/vnd.pdf") || contentType.equals("text/pdf"));

            if (directoryKey.startsWith("stories/cover/")) {
                imageValidator.validateSquareImage(file);
            }
            // 1️⃣ VALIDATION
            if (isImage && !directoryKey.startsWith("stories/cover/")) {
                imageValidator.validateCover(file);
            }

            if (isPdf) {
                pdfValidator.validatePdf(file);
            }

            // 2️⃣ BUILD KEY
            String originalFilename = file.getOriginalFilename();
            String extension = "";

            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
            }

            String fileName = UUID.randomUUID() + extension;
            String normalizedDir =
                    directoryKey.endsWith("/")
                            ? directoryKey.substring(0, directoryKey.length() - 1)
                            : directoryKey;
            String finalKey = normalizedDir + "/" + fileName;

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(finalKey)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return finalKey;

        } catch (IllegalArgumentException ex) {
            // ❗ validation errors (image/pdf)
            throw ex;

        } catch (Exception ex) {
            log.error("S3/R2 upload failed", ex);
            throw new S3UploadException(ApiMessageKey.S3_UPLOAD_UNEXPECTED_ERROR, ex);
        }
    }

    // ======================================================
    // GET FILE URL
    // ======================================================
    @Override
    public String getFileUrl(String keyName, UrlStrategy strategy) {
        if (strategy == UrlStrategy.PUBLIC_READ) {
            return buildPublicUrl(keyName);
        }
        return buildPreSignedGetUrl(keyName, Duration.ofMinutes(expirationMinutes));
    }

    private String buildPublicUrl(String keyName) {
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl.replaceAll("/+$", "") + "/" + keyName;
        }
        if (accountId != null && !accountId.isBlank()) {
            return String.format("https://%s.%s.r2.cloudflarestorage.com/%s", bucketName, accountId, keyName);
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, keyName);
    }

    private String buildPreSignedGetUrl(String keyName, Duration duration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // ======================================================
    // DELETE FILE
    // ======================================================
    @Override
    public void deleteFile(String keyName) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .build();
            s3Client.deleteObject(request);
        } catch (Exception ex) {
            log.error("S3/R2 delete failed", ex);
            throw new S3UploadException(ApiMessageKey.S3_DELETE_UNEXPECTED_ERROR, ex);
        }
    }

    @Override
    public String storeBytes(byte[] content, String contentType, String directoryKey, String extension) {
        try {
            String fileName = UUID.randomUUID() + (extension.startsWith(".") ? extension : "." + extension);
            String normalizedDir =
                    directoryKey.endsWith("/")
                            ? directoryKey.substring(0, directoryKey.length() - 1)
                            : directoryKey;
            String finalKey = normalizedDir + "/" + fileName;

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(finalKey)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));

            return finalKey;
        } catch (Exception ex) {
            log.error("S3/R2 upload bytes failed", ex);
            throw new S3UploadException(ApiMessageKey.S3_UPLOAD_UNEXPECTED_ERROR, ex);
        }
    }

    @Override
    public byte[] getBytes(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            try (ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(request)) {
                return inputStream.readAllBytes();
            }
        } catch (Exception e) {
            log.error("Failed to download bytes from S3/R2 for key {}: {}", key, e.getMessage());
            throw new S3UploadException(ApiMessageKey.S3_UPLOAD_UNEXPECTED_ERROR, e);
        }
    }
}

