package com.doova.ktab.features.ocr.storage;

import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.repository.attachment.AttachmentRepository;
import com.doova.ktab.repository.book.BookRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("development")
public class CloudflareR2UploadIntegrationTest {

    @Autowired
    private S3OcrStorageService s3OcrStorage;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}")
    private String bucketName;

    @Test
    @DisplayName("Push all 54 rendered pages and source PDF to Cloudflare R2 for Book 124")
    void pushPagesToCloudflareR2() throws Exception {
        System.out.println("=== 1. Checking Cloudflare R2 Bucket Connection ===");
        System.out.println("Bucket name: " + bucketName);
        assertDoesNotThrow(() -> s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()),
                "Cloudflare R2 bucket must be accessible");
        System.out.println("✅ Cloudflare R2 bucket connection verified successfully!");

        Long bookId = 124L;
        File pdfFile = new File("C:/Users/PC/.gemini/antigravity-ide/brain/b1952b0a-76c8-4070-8403-37ac063f64f6/.user_uploaded/media_1789967820810.pdf");
        assertTrue(pdfFile.exists(), "Source PDF must exist");

        // 2. Upload source PDF
        System.out.println("\n=== 2. Uploading Source PDF to Cloudflare R2 ===");
        String pdfKey;
        try (FileInputStream fis = new FileInputStream(pdfFile)) {
            pdfKey = s3OcrStorage.putBookPdf(bookId, fis, pdfFile.length());
        }
        System.out.println("✅ Source PDF uploaded: " + pdfKey);

        // 3. Render and Upload all 54 pages to Cloudflare R2
        System.out.println("\n=== 3. Rendering and Pushing 54 Pages to Cloudflare R2 ===");
        long startTime = System.currentTimeMillis();

        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            int totalPages = doc.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(doc);

            for (int i = 0; i < totalPages; i++) {
                int pageNum = i + 1;
                BufferedImage img = renderer.renderImageWithDPI(i, 200);

                byte[] pngBytes;
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(img, "png", baos);
                    pngBytes = baos.toByteArray();
                }

                s3OcrStorage.uploadPagePng(bookId, pageNum, pngBytes);

                if (pageNum % 10 == 0 || pageNum == totalPages) {
                    System.out.printf("   Uploaded Page %2d / %d to Cloudflare R2 (%d KB)%n",
                            pageNum, totalPages, pngBytes.length / 1024);
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("✅ All 54 pages rendered and pushed to Cloudflare R2 in %d ms (%.2f s)%n",
                duration, duration / 1000.0);

        // 4. Verify listPageKeys from Cloudflare R2
        System.out.println("\n=== 4. Verifying Objects in Cloudflare R2 ===");
        List<String> pageKeys = s3OcrStorage.listPageKeys(bookId);
        System.out.printf("Found %d page objects in Cloudflare R2 for book %d:%n", pageKeys.size(), bookId);
        assertEquals(54, pageKeys.size(), "All 54 page objects must exist in Cloudflare R2");
        System.out.println("   First page: " + pageKeys.get(0));
        System.out.println("   Last page:  " + pageKeys.get(pageKeys.size() - 1));

        // 5. Verify Presigned URL generation
        String testUrl = s3OcrStorage.generatePresignedUrl(pageKeys.get(0));
        System.out.println("\n=== 5. Sample Presigned URL ===");
        System.out.println("   Page 1 URL: " + testUrl);
        assertNotNull(testUrl);
        assertTrue(testUrl.contains("r2.cloudflarestorage.com") || testUrl.contains("r2.dev"));

        // 6. Record in tbl_attachments if not already present
        System.out.println("\n=== 6. Updating tbl_attachments for Book 124 ===");
        List<Attachment> existingAttachments = attachmentRepository.findAllByEntityIdAndEntityType(bookId, "Book");
        boolean hasPdf = existingAttachments.stream().anyMatch(a -> "PDF_SOURCE".equals(a.getType()));
        if (!hasPdf) {
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setEntityId(bookId);
            pdfAttachment.setEntityType("Book");
            pdfAttachment.setType("PDF_SOURCE");
            pdfAttachment.setFileName("الأجنحة المتكسرة.pdf");
            pdfAttachment.setStoragePath(pdfKey);
            pdfAttachment.setMimeType("application/pdf");
            pdfAttachment.setFileSize(pdfFile.length());
            attachmentRepository.save(pdfAttachment);
            System.out.println("   Saved PDF_SOURCE attachment: " + pdfKey);
        }

        boolean hasCover = existingAttachments.stream().anyMatch(a -> "COVER_IMAGE".equals(a.getType()));
        if (!hasCover) {
            Attachment coverAttachment = new Attachment();
            coverAttachment.setEntityId(bookId);
            coverAttachment.setEntityType("Book");
            coverAttachment.setType("COVER_IMAGE");
            coverAttachment.setFileName("cover.png");
            coverAttachment.setStoragePath(pageKeys.get(0));
            coverAttachment.setMimeType("image/png");
            attachmentRepository.save(coverAttachment);
            System.out.println("   Saved COVER_IMAGE attachment: " + pageKeys.get(0));
        }

        System.out.println("\n🎉 SUCCESS: All 54 pages and book attachments pushed to Cloudflare R2 and synced!");
    }
}
