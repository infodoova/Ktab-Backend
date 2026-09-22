package com.doova.ktab.features.ocr.batch;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.ReadingDirection;
import com.doova.ktab.enums.book.SpreadSide;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ocr.image.*;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class PageImagePreparationTasklet implements Tasklet {

    private final PageRenderer pageRenderer;
    private final BorderCropper borderCropper;
    private final SpreadDetector spreadDetector;
    private final SpreadSplitter spreadSplitter;
    private final OrientationPreChecker orientationPreChecker;
    private final ImageQualityAnalyzer imageQualityAnalyzer;
    private final S3OcrStorageService s3;
    private final BookRepository bookRepository;
    private final BookPageRepository pageRepository;
    private final Long bookId;
    private final String pdfS3Key;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("PageImagePreparation START bookId={} key={}", bookId, pdfS3Key);
        meterRegistry.counter("ocr.decomposition.jobs.started").increment();

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        ReadingDirection direction = book.getReadingDirection() != null
                ? book.getReadingDirection()
                : ReadingDirection.RTL;

        try (InputStream in = s3.getStream(pdfS3Key);
             RandomAccessRead rar = new RandomAccessReadBuffer(in);
             PDDocument doc = Loader.loadPDF(rar)) {

            PDFRenderer pdfRenderer = new PDFRenderer(doc);
            int totalPdfPages = doc.getNumberOfPages();
            log.info("Decomposing book {} with {} source PDF pages, reading direction={}",
                    bookId, totalPdfPages, direction);

            int currentBookPageNumber = 1;

            for (int pdfPageIndex = 0; pdfPageIndex < totalPdfPages; pdfPageIndex++) {
                int sourcePdfPage = pdfPageIndex + 1;

                // 1. Render page with DPI clamping
                PageRenderer.RenderedPage rendered = pageRenderer.render(doc, pdfRenderer, pdfPageIndex);
                BufferedImage currentImage = rendered.image();

                // 2. Crop dark scan borders
                BorderCropper.CroppedResult cropped = borderCropper.crop(currentImage);
                currentImage = cropped.image();

                // 3. Detect and split spreads
                Optional<Integer> gutterX = spreadDetector.detectGutter(currentImage);

                if (gutterX.isPresent()) {
                    meterRegistry.counter("ocr.decomposition.spreads.detected").increment();
                    List<SpreadSplitter.SplitPage> splitPages = spreadSplitter.split(currentImage, gutterX.get(), direction);

                    for (SpreadSplitter.SplitPage splitPage : splitPages) {
                        BufferedImage pageImg = splitPage.image();
                        BorderCropper.CroppedResult subCrop = borderCropper.crop(pageImg);
                        pageImg = subCrop.image();

                        savePreparedPage(
                                book,
                                currentBookPageNumber++,
                                sourcePdfPage,
                                splitPage.side(),
                                rendered.effectiveDpi(),
                                pageImg,
                                subCrop.borderCropSkipped()
                        );
                    }
                } else {
                    // Single page
                    savePreparedPage(
                            book,
                            currentBookPageNumber++,
                            sourcePdfPage,
                            SpreadSide.NONE,
                            rendered.effectiveDpi(),
                            currentImage,
                            cropped.borderCropSkipped()
                    );
                }

                currentImage.flush();
            }

            int finalTotalPages = currentBookPageNumber - 1;
            book.setPageCount(finalTotalPages);
            bookRepository.save(book);

            chunkContext.getStepContext().getStepExecution().getJobExecution()
                    .getExecutionContext().putInt("pageCount", finalTotalPages);

            log.info("PageImagePreparation DONE for bookId={} with {} total book pages", bookId, finalTotalPages);
            meterRegistry.counter("ocr.decomposition.jobs.completed").increment();
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            log.error("PageImagePreparation FAILED for bookId={}", bookId, e);
            meterRegistry.counter("ocr.decomposition.jobs.failed").increment();
            throw e;
        }
    }

    private void savePreparedPage(
            Book book,
            int bookPageNumber,
            int sourcePdfPage,
            SpreadSide spreadSide,
            int renderDpi,
            BufferedImage image,
            boolean borderCropSkipped
    ) throws Exception {
        // Image quality analysis
        ImageQualityAnalyzer.ImageQualityAnalysis analysis = imageQualityAnalyzer.analyze(image);
        if (borderCropSkipped) {
            analysis.metrics().put("borderCropSkipped", true);
        }

        // Encode and upload image to S3
        byte[] imageBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            imageBytes = baos.toByteArray();
        }

        s3.uploadPagePng(book.getId(), bookPageNumber, imageBytes);
        meterRegistry.counter("ocr.decomposition.pages.uploaded").increment();

        // Check if page row already exists (upsert / idempotent)
        Optional<BookPage> existing = pageRepository.findByBookIdAndPageNumber(book.getId(), bookPageNumber);
        BookPage page = existing.orElseGet(BookPage::new);

        page.setBook(book);
        page.setPageNumber(bookPageNumber);
        page.setSourcePdfPage(sourcePdfPage);
        page.setSpreadSide(spreadSide);
        page.setRotationDegrees(0);
        page.setRenderDpi(renderDpi);
        page.setImageWidth(image.getWidth());
        page.setImageHeight(image.getHeight());
        page.setImageQuality(analysis.quality());
        page.setImageMetrics(objectMapper.writeValueAsString(analysis.metrics()));
        page.setPageKind(PageKind.UNKNOWN);
        page.setStatus(OcrStatus.PENDING);

        pageRepository.save(page);
    }
}
