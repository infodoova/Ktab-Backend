package com.doova.ktab.service.file.impl;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.exception.S3UploadException;
import com.doova.ktab.utils.validator.ImageValidator;
import com.doova.ktab.utils.validator.PdfValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
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
import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private ImageValidator imageValidator;

    @Mock
    private PdfValidator pdfValidator;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client, s3Presigner, imageValidator, pdfValidator);
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "accountId", "test-account-id");
        ReflectionTestUtils.setField(s3Service, "region", "auto");
        ReflectionTestUtils.setField(s3Service, "expirationMinutes", 15L);
        ReflectionTestUtils.setField(s3Service, "publicUrl", "");
    }

    @Test
    @DisplayName("storeFile uploads image successfully and validates cover")
    void storeFile_ImageCover_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", "fake-image".getBytes());

        String key = s3Service.storeFile(file, "books/1/covers");

        assertNotNull(key);
        assertTrue(key.startsWith("books/1/covers/"));
        assertTrue(key.endsWith(".png"));

        verify(imageValidator).validateCover(file);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("storeFile for story cover validates square image")
    void storeFile_StoryCover_ValidatesSquare() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "square.jpg", "image/jpeg", "fake-image".getBytes());

        String key = s3Service.storeFile(file, "stories/cover/123");

        assertNotNull(key);
        assertTrue(key.startsWith("stories/cover/123/"));

        verify(imageValidator).validateSquareImage(file);
        verify(imageValidator, never()).validateCover(file);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("storeFile uploads PDF successfully and validates PDF")
    void storeFile_Pdf_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", "fake-pdf".getBytes());

        String key = s3Service.storeFile(file, "books/1/pdf");

        assertNotNull(key);
        assertTrue(key.startsWith("books/1/pdf/"));
        assertTrue(key.endsWith(".pdf"));

        verify(pdfValidator).validatePdf(file);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("storeFile throws S3UploadException when S3Client fails")
    void storeFile_S3Error_ThrowsS3UploadException() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", "fake-image".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Connection timeout").build());

        assertThrows(S3UploadException.class, () -> s3Service.storeFile(file, "books/1/covers"));
    }

    @Test
    @DisplayName("getFileUrl with PUBLIC_READ returns R2 public URL format")
    void getFileUrl_PublicRead_WithoutCustomDomain_ReturnsR2Url() {
        String url = s3Service.getFileUrl("books/1/covers/test.png", UrlStrategy.PUBLIC_READ);

        assertEquals("https://test-bucket.test-account-id.r2.cloudflarestorage.com/books/1/covers/test.png", url);
    }

    @Test
    @DisplayName("getFileUrl with PUBLIC_READ and custom publicUrl returns custom domain URL")
    void getFileUrl_PublicRead_WithCustomDomain_ReturnsCustomDomainUrl() {
        ReflectionTestUtils.setField(s3Service, "publicUrl", "https://cdn.ktab.app");

        String url = s3Service.getFileUrl("books/1/covers/test.png", UrlStrategy.PUBLIC_READ);

        assertEquals("https://cdn.ktab.app/books/1/covers/test.png", url);
    }

    @Test
    @DisplayName("getFileUrl with SIGNED returns presigned URL from S3Presigner")
    void getFileUrl_Signed_ReturnsPresignedUrl() throws Exception {
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        URL presignedUrl = URI.create("https://presigned.r2.cloudflarestorage.com/books/1/covers/test.png?token=123").toURL();
        when(presignedRequest.url()).thenReturn(presignedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        String url = s3Service.getFileUrl("books/1/covers/test.png", UrlStrategy.SIGNED);

        assertEquals(presignedUrl.toString(), url);
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("deleteFile calls S3Client.deleteObject")
    void deleteFile_Success() {
        s3Service.deleteFile("books/1/covers/test.png");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("books/1/covers/test.png", captor.getValue().key());
    }

    @Test
    @DisplayName("deleteFile wraps S3 exception in S3UploadException")
    void deleteFile_Error_ThrowsS3UploadException() {
        doThrow(S3Exception.builder().message("Access denied").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThrows(S3UploadException.class, () -> s3Service.deleteFile("books/1/covers/test.png"));
    }

    @Test
    @DisplayName("storeBytes uploads byte array to storage")
    void storeBytes_Success() {
        byte[] data = "raw audio data".getBytes();
        String key = s3Service.storeBytes(data, "audio/mpeg", "books/1/tts", "mp3");

        assertNotNull(key);
        assertTrue(key.startsWith("books/1/tts/"));
        assertTrue(key.endsWith(".mp3"));

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("getBytes retrieves byte array from storage")
    void getBytes_Success() {
        byte[] expected = "test-bytes".getBytes();
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(expected))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);

        byte[] actual = s3Service.getBytes("books/1/cover.jpg");

        assertArrayEquals(expected, actual);
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }
}
