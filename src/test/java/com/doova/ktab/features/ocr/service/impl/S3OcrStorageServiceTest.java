package com.doova.ktab.features.ocr.service.impl;

import com.doova.ktab.features.ocr.batch.PageItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3OcrStorageServiceTest {

    @Mock
    private S3Client s3;

    @Mock
    private S3Presigner s3Presigner;

    private S3OcrStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new S3OcrStorageService(s3, s3Presigner);
        ReflectionTestUtils.setField(storageService, "bucket", "ocr-bucket");
        ReflectionTestUtils.setField(storageService, "pdfPrefix", "books");
        ReflectionTestUtils.setField(storageService, "pagesPrefix", "books");
        ReflectionTestUtils.setField(storageService, "deadLetterPrefix", "dead-letter/books");
        ReflectionTestUtils.setField(storageService, "presignedExpirationMinutes", 30);
    }

    @Test
    @DisplayName("putBookPdf uploads source PDF to R2/S3")
    void putBookPdf_Success() {
        byte[] pdfContent = "pdf-content".getBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(pdfContent);

        String key = storageService.putBookPdf(42L, in, pdfContent.length);

        assertEquals("books/42/source.pdf", key);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("ocr-bucket", captor.getValue().bucket());
        assertEquals("books/42/source.pdf", captor.getValue().key());
        assertEquals("application/pdf", captor.getValue().contentType());
    }

    @Test
    @DisplayName("getStream retrieves object stream from storage")
    void getStream_Success() {
        ResponseInputStream<GetObjectResponse> mockStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("data".getBytes()))
        );
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(mockStream);

        InputStream stream = storageService.getStream("books/42/source.pdf");
        assertNotNull(stream);
        verify(s3).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("generatePresignedUrl returns presigned URL for image")
    void generatePresignedUrl_Success() throws Exception {
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        URL presignedUrl = URI.create("https://r2.cloudflarestorage.com/ocr-bucket/page.png?token=xyz").toURL();
        when(presignedRequest.url()).thenReturn(presignedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        String url = storageService.generatePresignedUrl("books/42/pages/page-0001.png", Duration.ofMinutes(10));
        assertEquals(presignedUrl.toString(), url);
    }

    @Test
    @DisplayName("uploadPagePng uploads page PNG with correct naming convention")
    void uploadPagePng_Success() {
        byte[] imageBytes = "png-bytes".getBytes();
        storageService.uploadPagePng(42L, 5, imageBytes);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("ocr-bucket", captor.getValue().bucket());
        assertEquals("books/42/pages/page-0005.png", captor.getValue().key());
        assertEquals("image/png", captor.getValue().contentType());
    }

    @Test
    @DisplayName("listPageKeys lists and sorts page keys deterministically")
    void listPageKeys_Success() {
        S3Object obj2 = S3Object.builder().key("books/42/pages/page-0002.png").build();
        S3Object obj1 = S3Object.builder().key("books/42/pages/page-0001.png").build();
        S3Object nonImg = S3Object.builder().key("books/42/pages/readme.txt").build();

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(List.of(obj2, nonImg, obj1))
                .isTruncated(false)
                .build();
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        List<String> keys = storageService.listPageKeys(42L);

        assertEquals(2, keys.size());
        assertEquals("books/42/pages/page-0001.png", keys.get(0));
        assertEquals("books/42/pages/page-0002.png", keys.get(1));
    }

    @Test
    @DisplayName("listPages converts page keys to PageItems with presigned URLs")
    void listPages_Success() throws Exception {
        S3Object obj1 = S3Object.builder().key("books/42/pages/page-0001.png").build();
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(List.of(obj1))
                .isTruncated(false)
                .build();
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        URL presignedUrl = URI.create("https://r2.url/page-0001.png").toURL();
        when(presignedRequest.url()).thenReturn(presignedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        List<PageItem> pages = storageService.listPages(42L);

        assertEquals(1, pages.size());
        PageItem item = pages.get(0);
        assertEquals(42L, item.bookId());
        assertEquals(1, item.pageNumber());
        assertEquals("books/42/pages/page-0001.png", item.s3Key());
        assertEquals("image/png", item.mime());
        assertEquals("https://r2.url/page-0001.png", item.presignedUrl());
    }

    @Test
    @DisplayName("moveToDeadLetter copies failed object to dead-letter prefix with reason")
    void moveToDeadLetter_Success() {
        storageService.moveToDeadLetter("books/42/pages/page-0003.png", 42L, 3, "Image corrupted");

        ArgumentCaptor<CopyObjectRequest> captor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3).copyObject(captor.capture());

        assertEquals("ocr-bucket", captor.getValue().destinationBucket());
        assertEquals("dead-letter/books/42/page-0003.png", captor.getValue().destinationKey());
        assertEquals("Image corrupted", captor.getValue().metadata().get("dlq-reason"));
    }

    @Test
    @DisplayName("deletePages deletes all pages for a book in batch")
    void deletePages_Success() {
        S3Object obj1 = S3Object.builder().key("books/42/pages/page-0001.png").build();
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(List.of(obj1))
                .isTruncated(false)
                .build();
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        storageService.deletePages(42L);

        verify(s3).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
