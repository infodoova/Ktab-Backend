package com.doova.ktab.features.ocr.web;

import com.doova.ktab.features.ingestion.config.OcrSwitchProperties;
import com.doova.ktab.features.ocr.image.BorderCropper;
import com.doova.ktab.features.ocr.image.PageRenumberer;
import com.doova.ktab.features.ocr.image.SpreadSplitter;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OcrJobControllerOcrSwitchTest {

    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final OcrSwitchProperties ocr = new OcrSwitchProperties();
    private final org.springframework.context.MessageSource messages = mock(org.springframework.context.MessageSource.class);
    private final OcrJobController controller = new OcrJobController(mock(S3OcrStorageService.class), jobLauncher,
            mock(Job.class), mock(Job.class), mock(Job.class), mock(JobExplorer.class), mock(AttachmentService.class),
            mock(BookRepository.class), mock(BookPageRepository.class), mock(PageRenumberer.class),
            mock(SpreadSplitter.class), mock(BorderCropper.class), ocr, messages);

    @Test
    void everyLaunchEndpointAnswers409AndLaunchesNothingWhileOcrIsOff() throws Exception {
        org.mockito.Mockito.when(messages.getMessage(org.mockito.ArgumentMatchers.eq("ocr.disabled"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(java.util.Locale.class)))
                .thenReturn("OCR is disabled (KTAB_OCR_ENABLED=false)");
        for (ResponseEntity<com.doova.ktab.dto.ApiResponse<Map<String, Object>>> r : List.of(
                controller.start(1L), controller.restructure(1L), controller.harmonize(1L),
                controller.retryFlagged(1L), controller.resumeLatest(1L))) {
            assertThat(r.getStatusCode().value()).isEqualTo(409);
            assertThat(r.getBody().isSuccess()).isFalse();
            assertThat(r.getBody().getMessage()).contains("OCR is disabled").contains("KTAB_OCR_ENABLED");
        }
        verifyNoInteractions(jobLauncher);
    }
}
