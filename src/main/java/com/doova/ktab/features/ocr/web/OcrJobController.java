package com.doova.ktab.features.ocr.web;

import com.doova.ktab.enums.book.ReadingDirection;
import com.doova.ktab.enums.book.SpreadSide;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ingestion.config.OcrSwitchProperties;
import com.doova.ktab.features.ocr.image.BorderCropper;
import com.doova.ktab.features.ocr.image.OrientationPreChecker;
import com.doova.ktab.features.ocr.image.PageRenumberer;
import com.doova.ktab.features.ocr.image.SpreadSplitter;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ocr")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN')")
public class OcrJobController {

    private final S3OcrStorageService s3;
    private final JobLauncher jobLauncher;
    @Qualifier("ocrJob")
    private final Job ocrJob;
    @Qualifier("restructureJob")
    private final Job restructureJob;
    @Qualifier("harmonizeJob")
    private final Job harmonizeJob;
    private final JobExplorer jobExplorer;
    private final AttachmentService attachmentService;
    private final BookRepository bookRepository;
    private final BookPageRepository pageRepository;
    private final PageRenumberer pageRenumberer;
    private final SpreadSplitter spreadSplitter;
    private final BorderCropper borderCropper;
    private final OcrSwitchProperties ocrSwitch;

    /** 409 while OCR is switched off (KTAB_OCR_ENABLED=false); null when OCR may run. */
    private ResponseEntity<Map<String, Object>> ocrDisabled() {
        return ocrSwitch.isEnabled() ? null
                : ResponseEntity.status(409).body(Map.of("message", "OCR is disabled (KTAB_OCR_ENABLED=false)"));
    }

    /**
     * Upload PDF to S3 and start full OCR pipeline.
     */
    @PostMapping("/books/{bookId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable Long bookId) throws Exception {
        ResponseEntity<Map<String, Object>> disabled = ocrDisabled();
        if (disabled != null) {
            return disabled;
        }
        Optional<JobExecution> running = jobExplorer.findRunningJobExecutions("ocrJob").stream()
                .filter(exec -> bookId.equals(exec.getJobParameters().getLong("bookId")))
                .findFirst();

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
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution exec = jobLauncher.run(ocrJob, params);
        return ResponseEntity.accepted().body(Map.of("executionId", exec.getId(), "bookId", bookId, "pdfKey", pdfKey, "status", exec.getStatus().toString()));
    }

    /**
     * Restructure job: runs Steps 3 (TOC), 4 (Structure), 6 (Quality) without paying for OCR again.
     */
    @PostMapping("/books/{bookId}/restructure")
    public ResponseEntity<Map<String, Object>> restructure(@PathVariable Long bookId) throws Exception {
        ResponseEntity<Map<String, Object>> disabled = ocrDisabled();
        if (disabled != null) {
            return disabled;
        }
        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution exec = jobLauncher.run(restructureJob, params);
        return ResponseEntity.accepted().body(Map.of("executionId", exec.getId(), "bookId", bookId, "status", exec.getStatus().toString()));
    }

    /**
     * Harmonize job: runs Steps 5 (Harmonize) and 6 (Quality).
     */
    @PostMapping("/books/{bookId}/harmonize")
    public ResponseEntity<Map<String, Object>> harmonize(@PathVariable Long bookId) throws Exception {
        ResponseEntity<Map<String, Object>> disabled = ocrDisabled();
        if (disabled != null) {
            return disabled;
        }
        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution exec = jobLauncher.run(harmonizeJob, params);
        return ResponseEntity.accepted().body(Map.of("executionId", exec.getId(), "bookId", bookId, "status", exec.getStatus().toString()));
    }

    /**
     * Retry FLAGGED or FAILED pages only.
     */
    @PostMapping("/books/{bookId}/pages/retry-flagged")
    @Transactional
    public ResponseEntity<Map<String, Object>> retryFlagged(@PathVariable Long bookId) throws Exception {
        ResponseEntity<Map<String, Object>> disabled = ocrDisabled();
        if (disabled != null) {
            return disabled;
        }
        List<BookPage> flaggedPages = pageRepository.findByBookIdAndStatusInOrderByPageNumberAsc(
                bookId,
                List.of(OcrStatus.FLAGGED, OcrStatus.FAILED)
        );

        for (BookPage p : flaggedPages) {
            p.setStatus(OcrStatus.PENDING);
        }
        pageRepository.saveAll(flaggedPages);

        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution exec = jobLauncher.run(ocrJob, params);
        return ResponseEntity.accepted().body(Map.of(
                "retriedPagesCount", flaggedPages.size(),
                "executionId", exec.getId(),
                "status", exec.getStatus().toString()
        ));
    }

    /**
     * Manually rotate a page image by degrees (90, 180, 270) and set status to PENDING for re-OCR.
     */
    @PostMapping("/books/{bookId}/pages/{pageNumber}/rotate")
    @Transactional
    public ResponseEntity<Map<String, Object>> rotatePage(
            @PathVariable Long bookId,
            @PathVariable int pageNumber,
            @RequestParam int degrees
    ) throws Exception {
        Optional<BookPage> pageOpt = pageRepository.findByBookIdAndPageNumber(bookId, pageNumber);
        if (pageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BookPage page = pageOpt.get();
        String key = String.format("books/%d/pages/page-%04d.png", bookId, pageNumber);
        byte[] bytes = s3.getPageBytes(key);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        BufferedImage rotated = OrientationPreChecker.rotate(img, degrees);

        byte[] rotatedBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(rotated, "png", baos);
            rotatedBytes = baos.toByteArray();
        }

        s3.uploadPagePng(bookId, pageNumber, rotatedBytes);

        page.setRotationDegrees((page.getRotationDegrees() + degrees) % 360);
        page.setStatus(OcrStatus.PENDING);
        pageRepository.save(page);

        return ResponseEntity.ok(Map.of(
                "bookId", bookId,
                "pageNumber", pageNumber,
                "rotationDegrees", page.getRotationDegrees(),
                "status", "PENDING"
        ));
    }

    /**
     * Manually split an unsplit spread page into two pages and renumber subsequent pages.
     */
    @PostMapping("/books/{bookId}/pages/{pageNumber}/split")
    @Transactional
    public ResponseEntity<Map<String, Object>> splitPage(
            @PathVariable Long bookId,
            @PathVariable int pageNumber
    ) throws Exception {
        Optional<BookPage> pageOpt = pageRepository.findByBookIdAndPageNumber(bookId, pageNumber);
        if (pageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Book book = bookRepository.findById(bookId).orElseThrow();
        ReadingDirection direction = book.getReadingDirection() != null ? book.getReadingDirection() : ReadingDirection.RTL;

        BookPage page = pageOpt.get();
        String key = String.format("books/%d/pages/page-%04d.png", bookId, pageNumber);
        byte[] bytes = s3.getPageBytes(key);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));

        int gutterX = img.getWidth() / 2;
        List<SpreadSplitter.SplitPage> halves = spreadSplitter.split(img, gutterX, direction);

        // Shift subsequent pages by 1
        pageRenumberer.shiftPageNumbers(bookId, pageNumber + 1, 1);

        // Upload first half as pageNumber
        BufferedImage firstImg = borderCropper.crop(halves.get(0).image()).image();
        byte[] firstBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(firstImg, "png", baos);
            firstBytes = baos.toByteArray();
        }
        s3.uploadPagePng(bookId, pageNumber, firstBytes);
        page.setSpreadSide(halves.get(0).side());
        page.setStatus(OcrStatus.PENDING);
        pageRepository.save(page);

        // Upload second half as pageNumber + 1
        BufferedImage secondImg = borderCropper.crop(halves.get(1).image()).image();
        byte[] secondBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(secondImg, "png", baos);
            secondBytes = baos.toByteArray();
        }
        int newPageNum = pageNumber + 1;
        s3.uploadPagePng(bookId, newPageNum, secondBytes);

        BookPage newPage = new BookPage();
        newPage.setBook(book);
        newPage.setPageNumber(newPageNum);
        newPage.setSourcePdfPage(page.getSourcePdfPage());
        newPage.setSpreadSide(halves.get(1).side());
        newPage.setStatus(OcrStatus.PENDING);
        pageRepository.save(newPage);

        return ResponseEntity.ok(Map.of(
                "bookId", bookId,
                "splitPageNumber", pageNumber,
                "newPageNumber", newPageNum,
                "status", "PENDING"
        ));
    }

    /**
     * Resume the latest FAILED / STOPPED job for this book.
     */
    @PostMapping("/books/{bookId}/resume-latest")
    public ResponseEntity<Map<String, Object>> resumeLatest(@PathVariable Long bookId) throws Exception {
        ResponseEntity<Map<String, Object>> disabled = ocrDisabled();
        if (disabled != null) {
            return disabled;
        }
        JobExecution latest = findLatestForBook(bookId);
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        if (latest.getStatus() == BatchStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(Map.of("message", "Latest job already COMPLETED"));
        }

        JobExecution exec = jobLauncher.run(ocrJob, latest.getJobParameters());
        return ResponseEntity.accepted().body(Map.of("resumedFromExecutionId", latest.getId(), "newExecutionId", exec.getId(), "status", exec.getStatus().toString()));
    }

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
