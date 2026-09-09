package com.doova.ktab.features.ocr.web;

import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ocr")
public class OcrJobController {

    private final S3OcrStorageService s3;
    private final JobLauncher jobLauncher;
    private final Job ocrJob;
    private final JobExplorer jobExplorer;
    private final AttachmentService attachmentService;

    /**
     * Upload PDF to S3 and start OCR job.
     * JobParameters are STABLE → allow restart/resume.
     */
    @PostMapping("/books/{bookId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable Long bookId) throws Exception {

        Optional<JobExecution> running = jobExplorer.findRunningJobExecutions("ocrJob").stream().filter(exec -> bookId.equals(exec.getJobParameters().getLong("bookId"))).findFirst();

        if (running.isPresent()) {
            return ResponseEntity.status(409).body(Map.of("status", "RUNNING", "executionId", running.get().getId()));
        }

        Optional<Attachment> attachment = attachmentService.getAttachment(bookId, "Book", "PDF_SOURCE");
        if (attachment.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No PDF_SOURCE attachment found for bookId " + bookId));
        }

        String pdfKey = attachment.get().getStoragePath();

        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addString("pdfKey", pdfKey)
                .addLong("run.id", System.currentTimeMillis()) // 👈 NEW INSTANCE
                .toJobParameters();

        JobExecution exec = jobLauncher.run(ocrJob, params);

        return ResponseEntity.accepted().body(Map.of("executionId", exec.getId(), "bookId", bookId, "pdfKey", pdfKey, "status", exec.getStatus().toString()));
    }

    /**
     * Resume the latest FAILED / STOPPED job for this book
     * by re-running the SAME JobParameters.
     */
    @PostMapping("/books/{bookId}/resume-latest")
    public ResponseEntity<Map<String, Object>> resumeLatest(@PathVariable Long bookId) throws Exception {

        JobExecution latest = findLatestForBook(bookId);

        if (latest == null) {
            return ResponseEntity.notFound().build();
        }

        if (latest.getStatus() == BatchStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(Map.of("message", "Latest job already COMPLETED"));
        }

        JobExecution exec = jobLauncher.run(ocrJob, latest.getJobParameters() // SAME params → restart
        );

        return ResponseEntity.accepted().body(Map.of("resumedFromExecutionId", latest.getId(), "newExecutionId", exec.getId(), "status", exec.getStatus().toString()));
    }

    /**
     * Find latest JobExecution for a bookId
     */
    private JobExecution findLatestForBook(Long bookId) {

        List<JobInstance> instances = jobExplorer.getJobInstances("ocrJob", 0, 50);

        JobExecution latest = null;

        for (JobInstance ji : instances) {
            for (JobExecution e : jobExplorer.getJobExecutions(ji)) {

                Long b = e.getJobParameters().getLong("bookId");

                if (b != null && b.equals(bookId)) {
                    if (latest == null || e.getCreateTime().isAfter(latest.getCreateTime())) {
                        latest = e;
                    }
                }
            }
        }

        return latest;
    }
}
