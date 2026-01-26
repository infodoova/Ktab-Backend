package com.doova.ktab.ocr.ai;

import com.doova.ktab.ocr.dto.GeminiOcrResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GeminiOcrService {

    private final ChatModel chatModel;
    private final ArabicPagePromptFactory promptFactory;
    private final MeterRegistry meterRegistry;
    private final HttpClient httpClient;

    public GeminiOcrService(
            @Qualifier("ocrGeminiModel") ChatModel chatModel,
            ArabicPagePromptFactory promptFactory,
            MeterRegistry meterRegistry
    ) {
        this.chatModel = chatModel;
        this.promptFactory = promptFactory;
        this.meterRegistry = meterRegistry;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * OCR a page using a presigned URL (memory-efficient).
     * Downloads image bytes on-demand and processes immediately.
     */
    public GeminiOcrResponse ocrOnePageFromUrl(String presignedUrl, String mime) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Download image bytes from presigned URL
            byte[] imageBytes = downloadImage(presignedUrl);
            meterRegistry.counter("ocr.gemini.image.downloads").increment();
            meterRegistry.summary("ocr.gemini.image.size.bytes").record(imageBytes.length);
            
            // Process with Gemini
            GeminiOcrResponse response = ocrOnePage(imageBytes, mime);
            
            sample.stop(meterRegistry.timer("ocr.gemini.api.duration", "status", "success"));
            return response;
            
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("ocr.gemini.api.duration", "status", "failure"));
            meterRegistry.counter("ocr.gemini.api.errors").increment();
            log.error("Failed to OCR page from URL: {}", e.getMessage());
            throw new RuntimeException("OCR from URL failed", e);
        }
    }

    /**
     * Download image bytes from a presigned URL.
     */
    private byte[] downloadImage(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download image: HTTP " + response.statusCode());
        }
        
        return response.body();
    }

    /**
     * OCR a page from raw bytes (original method).
     */
    public GeminiOcrResponse ocrOnePage(byte[] imageBytes, String mime) {
        Timer.Sample sample = Timer.start(meterRegistry);

        ChatResponse response = chatModel.call(promptFactory.buildPrompt(imageBytes, mime));

        String rawOutput = Optional.of(response)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse("")
                .trim();

        if (rawOutput.isEmpty()) {
            meterRegistry.counter("ocr.gemini.responses.empty").increment();
            return GeminiOcrResponse.empty();
        }

        // 1️⃣ Strict format (expected contract)
        Optional<GeminiOcrResponse> strict = parseStrictFormat(rawOutput);
        if (strict.isPresent()) {
            meterRegistry.counter("ocr.gemini.responses.parsed", "format", "strict").increment();
            sample.stop(meterRegistry.timer("ocr.gemini.parsing.duration"));
            return strict.get();
        }

        // 2️⃣ Relaxed format (Gemini drift tolerance)
        Optional<GeminiOcrResponse> relaxed = parseRelaxedFormat(rawOutput);
        if (relaxed.isPresent()) {
            meterRegistry.counter("ocr.gemini.responses.parsed", "format", "relaxed").increment();
            sample.stop(meterRegistry.timer("ocr.gemini.parsing.duration"));
            return relaxed.get();
        }

        // 3️⃣ Last resort fallback (text only, zero words)
        meterRegistry.counter("ocr.gemini.responses.parsed", "format", "fallback").increment();
        sample.stop(meterRegistry.timer("ocr.gemini.parsing.duration"));
        return GeminiOcrResponse.fallback(rawOutput);
    }

    /* -------------------------------------------------------
       Parsing strategies
       ------------------------------------------------------- */

    private Optional<GeminiOcrResponse> parseStrictFormat(String output) {
        Pattern pattern = Pattern.compile("(?is)text\\s*:\\s*(.*?)\\s*wordscount\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(output);

        if (!matcher.find()) {
            return Optional.empty();
        }

        String markdown = normalizeMarkdown(matcher.group(1));
        int wordCount = parseWordCount(matcher.group(2));

        return Optional.of(new GeminiOcrResponse(markdown, wordCount));
    }

    private Optional<GeminiOcrResponse> parseRelaxedFormat(String output) {
        Pattern textPattern = Pattern.compile("(?is)text\\s*:\\s*(.*)");
        Pattern countPattern = Pattern.compile("(?i)word[s]?\\s*count\\s*[:=]\\s*(\\d+)");

        Matcher textMatcher = textPattern.matcher(output);
        Matcher countMatcher = countPattern.matcher(output);

        if (!textMatcher.find()) {
            return Optional.empty();
        }

        String markdown = normalizeMarkdown(textMatcher.group(1));
        int wordCount = countMatcher.find() ? parseWordCount(countMatcher.group(1)) : estimateWordCount(markdown);

        return Optional.of(new GeminiOcrResponse(markdown, wordCount));
    }

    /* -------------------------------------------------------
       Helpers
       ------------------------------------------------------- */

    private String normalizeMarkdown(String markdown) {
        return markdown.replaceAll("\\r\\n?", "\n")   // normalize line endings
                .replaceAll("[ \\t]+$", "")    // trim line trailing spaces
                .trim();
    }

    private int parseWordCount(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int estimateWordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}

