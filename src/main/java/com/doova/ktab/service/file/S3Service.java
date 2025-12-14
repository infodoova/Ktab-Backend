package com.doova.ktab.service.file;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.doova.ktab.enums.UrlStrategy;
import com.doova.ktab.exceptions.S3UploadException;
import com.doova.ktab.service.interfaces.file.FileStorageService;
import com.doova.ktab.utils.validator.ImageValidator;
import com.doova.ktab.utils.validator.PdfValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service implements FileStorageService {

    private final AmazonS3 s3Client;
    private final ImageValidator imageValidator;
    private final PdfValidator pdfValidator;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;


    @Value("${aws.s3.url-strategy:SIGNED}")
    private UrlStrategy urlStrategy;

    @Value("${aws.s3.presigned.expiration-minutes:10}")
    private long expirationMinutes;

    // ========================== STORE FILE ===============================

    @Override
    public String storeFile(MultipartFile file, String directoryKey) throws IOException {
        try {
            // Content-type detection
            String contentType = file.getContentType();

            boolean isImage = contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/jpg"));

            boolean isPdf = contentType != null && (contentType.equals("application/pdf") || contentType.equals("application/x-pdf") || contentType.equals("application/acrobat") || contentType.equals("applications/vnd.pdf") || contentType.equals("text/pdf"));

            // 1️⃣ VALIDATION
            if (isImage) {
                imageValidator.validateCover(file);
            }
            if (isPdf) {
                pdfValidator.validatePdf(file);
            }

            // 2️⃣ Generate S3 key
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            int lastDot = originalFilename != null ? originalFilename.lastIndexOf('.') : -1;

            if (lastDot > 0) {
                extension = originalFilename.substring(lastDot);
            }

            String fileName = UUID.randomUUID().toString() + extension;
            String finalKey = directoryKey + "/" + fileName;

            log.info("Uploading file to S3: bucket={}, key={}", bucketName, finalKey);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);

            PutObjectRequest putRequest = new PutObjectRequest(bucketName, finalKey, file.getInputStream(), metadata);

            // If you want public-read objects in PUBLIC_READ mode:
            // if (urlStrategy == UrlStrategy.PUBLIC_READ) {
            //     putRequest.withCannedAcl(CannedAccessControlList.PublicRead);
            // }
//
//            if (isImage || isPdf) {
//                putRequest.withCannedAcl(CannedAccessControlList.PublicRead);
//            }

            s3Client.putObject(putRequest);

            return finalKey;

        } catch (IllegalArgumentException e) {
            // Validation errors
            throw e;
        } catch (Exception ex) {
            log.error("❌ Unexpected error while uploading to S3: {}", ex.getMessage());
            throw new S3UploadException("Unexpected error during file upload", ex);
        }
    }

    // =========================== GET URL ================================

    /**
     * Returns a file URL based on configured strategy:
     * - SIGNED → pre-signed URL with expiration
     * - PUBLIC_READ → plain HTTPS URL (no expiration)
     */
    @Override
    public String getFileUrl(String keyName, UrlStrategy urlStrategy) {
        if (urlStrategy == UrlStrategy.PUBLIC_READ) {
            // Public object URL (bucket policy or ACL must allow read)
            return buildPublicUrl(keyName);
        } else {
            // Default: pre-signed URL
            return buildPreSignedGetUrl(keyName, Duration.ofMinutes(expirationMinutes));
        }
    }

    // Explicit method if you ever want to request different expirations programmatically
    public String getFileUrlWithCustomExpiration(String keyName, Duration duration) {
        if (urlStrategy == UrlStrategy.PUBLIC_READ) {
            return buildPublicUrl(keyName);
        } else {
            return buildPreSignedGetUrl(keyName, duration);
        }
    }

    // ====================== INTERNAL URL BUILDERS =======================

    private String buildPublicUrl(String keyName) {
        // Standard virtual-hosted–style URL
        // e.g. https://your-bucket.s3.eu-central-1.amazonaws.com/books/cover/42/uuid.jpg
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, keyName);
    }

    private String buildPreSignedGetUrl(String keyName, Duration duration) {
        Date expiration = new Date(System.currentTimeMillis() + duration.toMillis());

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, keyName).withMethod(HttpMethod.GET).withExpiration(expiration);

        URL url = s3Client.generatePresignedUrl(request);
        return url.toString();
    }

    // =========================== DELETE =================================

    public void deleteFile(String keyName) {
        s3Client.deleteObject(bucketName, keyName);
    }

    // OPTIONAL: upload pre-signed URL (client-side PUT)
    public String generatePreSignedUrlForUpload(String keyName, Duration duration) {
        Date expiration = new Date(System.currentTimeMillis() + duration.toMillis());

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, keyName).withMethod(HttpMethod.PUT).withExpiration(expiration);

        URL url = s3Client.generatePresignedUrl(request);
        return url.toString();
    }
}
