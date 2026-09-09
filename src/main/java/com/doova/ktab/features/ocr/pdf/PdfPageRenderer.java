package com.doova.ktab.features.ocr.pdf;

import com.doova.ktab.features.ocr.batch.interfaces.PageRenderCallback;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class PdfPageRenderer {

    private final MeterRegistry meterRegistry;
    private final int parallelism;

    public PdfPageRenderer(
            MeterRegistry meterRegistry,
            @Value("${ktab.ocr.decomposition.parallelism:8}") int parallelism
    ) {
        this.meterRegistry = meterRegistry;
        this.parallelism = parallelism;
    }

    /**
     * PARALLEL page rendering using Java 21 virtual threads.
     * Renders pages concurrently and uploads to S3 in parallel.
     * Memory-efficient: each page is processed and released immediately.
     */
    public void renderPagesParallel(InputStream pdfStream, int dpi, PageRenderCallback callback) {
        Timer.Sample sample = Timer.start(meterRegistry);
        AtomicInteger processedCount = new AtomicInteger(0);

        try (
                RandomAccessRead rar = new RandomAccessReadBuffer(pdfStream);
                PDDocument doc = Loader.loadPDF(rar);
                ExecutorService executor = Executors.newFixedThreadPool(parallelism)
        ) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int totalPages = doc.getNumberOfPages();
            
            log.info("Starting parallel PDF decomposition: {} pages with parallelism {}", totalPages, parallelism);
            meterRegistry.gauge("ocr.decomposition.total_pages", totalPages);

            // Use a semaphore to limit concurrent memory usage
            Semaphore memorySemaphore = new Semaphore(parallelism);
            List<Future<?>> futures = new ArrayList<>(totalPages);

            for (int i = 0; i < totalPages; i++) {
                final int pageIndex = i;
                
                futures.add(executor.submit(() -> {
                    try {
                        memorySemaphore.acquire();
                        Timer.Sample pageSample = Timer.start(meterRegistry);
                        
                        // Render page (synchronized on renderer for thread safety)
                        BufferedImage img;
                        synchronized (renderer) {
                            img = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                        }

                        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                            ImageIO.write(img, "png", out);
                            byte[] pngBytes = out.toByteArray();

                            // Invoke callback (upload to S3)
                            callback.onPageRendered(pageIndex + 1, pngBytes);
                            
                            int completed = processedCount.incrementAndGet();
                            if (completed % 10 == 0) {
                                log.info("Decomposition progress: {}/{} pages", completed, totalPages);
                            }
                        } finally {
                            img.flush();
                        }
                        
                        pageSample.stop(meterRegistry.timer("ocr.decomposition.page.duration"));
                    } catch (Exception e) {
                        log.error("Failed to render page {}", pageIndex + 1, e);
                        meterRegistry.counter("ocr.decomposition.page.errors").increment();
                        throw new CompletionException(e);
                    } finally {
                        memorySemaphore.release();
                    }
                }));
            }

            // Wait for all pages to complete
            for (Future<?> future : futures) {
                try {
                    future.get(5, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    log.error("Page rendering timed out");
                    throw new IllegalStateException("PDF page rendering timed out", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("PDF page rendering failed", e.getCause());
                }
            }

            log.info("Parallel PDF decomposition completed: {} pages", totalPages);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PDF rendering interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("PDF parallel render failed", e);
        } finally {
            sample.stop(meterRegistry.timer("ocr.decomposition.total.duration"));
        }
    }

    /**
     * STREAMING approach (sequential) - kept for backward compatibility.
     * Processes one page at a time and passes it to the callback.
     */
    public void renderPagesStreaming(InputStream pdfStream, int dpi, PageRenderCallback callback) {
        try (
                RandomAccessRead rar = new RandomAccessReadBuffer(pdfStream);
                PDDocument doc = Loader.loadPDF(rar)
        ) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int totalPages = doc.getNumberOfPages();

            for (int i = 0; i < totalPages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);

                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    ImageIO.write(img, "png", out);
                    byte[] pngBytes = out.toByteArray();

                    // Invoke the callback (e.g., upload to S3)
                    callback.onPageRendered(i + 1, pngBytes);
                } finally {
                    // Explicitly flush image to help GC
                    img.flush();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("PDF streaming render failed", e);
        }
    }

    public List<byte[]> renderToPng(InputStream pdfStream, int dpi) {
        try (
                RandomAccessRead rar = new RandomAccessReadBuffer(pdfStream);
                PDDocument doc = Loader.loadPDF(rar)
        ) {

            PDFRenderer renderer = new PDFRenderer(doc);
            List<byte[]> pages = new ArrayList<>();

            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img =
                        renderer.renderImageWithDPI(i, dpi, ImageType.RGB);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(img, "png", out);

                pages.add(out.toByteArray());
                img.flush();
            }

            return pages;

        } catch (Exception e) {
            throw new IllegalStateException("PDF render failed", e);
        }
    }

    /**
     * PNG writer with explicit compression (lossless)
     */
    private byte[] writePng(BufferedImage image) throws Exception {

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        ImageWriter writer = writers.next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.9f); // lossless PNG, controls zlib level

        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageOutputStream ios = ImageIO.createImageOutputStream(baos)
        ) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
            return baos.toByteArray();
        }
    }
}
