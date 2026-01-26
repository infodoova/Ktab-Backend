package com.doova.ktab.service.file;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.enums.UrlStrategy;
import com.doova.ktab.exception.S3UploadException;
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

    // ======================================================
    // STORE FILE
    // ======================================================
    @Override
    public String storeFile(MultipartFile file, String directoryKey) throws IOException {

        try {
            String contentType = file.getContentType();

            boolean isImage = contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/jpg"));

            boolean isPdf = contentType != null && (contentType.equals("application/pdf") || contentType.equals("application/x-pdf") || contentType.equals("application/acrobat") || contentType.equals("applications/vnd.pdf") || contentType.equals("text/pdf"));

            if(directoryKey.startsWith("stories/cover/")){
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
            String finalKey = directoryKey + "/" + fileName;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);

            PutObjectRequest request = new PutObjectRequest(bucketName, finalKey, file.getInputStream(), metadata);

            s3Client.putObject(request);

            return finalKey;

        } catch (IllegalArgumentException ex) {
            // ❗ validation errors (image/pdf)
            throw ex;

        } catch (Exception ex) {
            log.error("S3 upload failed", ex);
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
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, keyName);
    }

    private String buildPreSignedGetUrl(String keyName, Duration duration) {
        Date expiration = new Date(System.currentTimeMillis() + duration.toMillis());

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, keyName).withMethod(HttpMethod.GET).withExpiration(expiration);

        URL url = s3Client.generatePresignedUrl(request);
        return url.toString();
    }

    // ======================================================
    // DELETE FILE
    // ======================================================
    @Override
    public void deleteFile(String keyName) {
        try {
            s3Client.deleteObject(bucketName, keyName);
        } catch (Exception ex) {
            log.error("S3 delete failed", ex);
            throw new S3UploadException(ApiMessageKey.S3_DELETE_UNEXPECTED_ERROR, ex);
        }
    }

    public String storeBytes(byte[] content, String contentType, String directoryKey, String extension) {
        try {
            String fileName = UUID.randomUUID() + (extension.startsWith(".") ? extension : "." + extension);
            String finalKey = directoryKey + "/" + fileName;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setContentType(contentType);

            PutObjectRequest request = new PutObjectRequest(bucketName, finalKey, new java.io.ByteArrayInputStream(content), metadata);
            s3Client.putObject(request);

            return finalKey;
        } catch (Exception ex) {
            log.error("S3 upload bytes failed", ex);
            throw new S3UploadException(ApiMessageKey.S3_UPLOAD_UNEXPECTED_ERROR, ex);
        }
    }

    public byte[] getBytes(String key) {
        try {
            S3Object s3Object = s3Client.getObject(bucketName, key);
            try (S3ObjectInputStream inputStream = s3Object.getObjectContent()) {
                return inputStream.readAllBytes();
            }
        } catch (Exception e) {
            log.error("Failed to download bytes from S3 for key {}: {}", key, e.getMessage());
            return null;
        }
    }
}
