package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Nano Banana (Gemini image) on Vertex AI through Ktab's existing {@code vertexGenAiClient}.
 * Deliberately NOT built on features.story.image.VertexImageClient: that class turns every
 * safety filter OFF, which is unacceptable for children's books.
 *
 * <p>The provider's quota is per minute, and a book asks for many pictures at once. Without a limit the jobs run into
 * HTTP 429 together, burn their few attempts within seconds and fail the whole book. So: at most
 * {@code maxConcurrentCalls} requests are in flight at a time; after a 429 every request waits (the quota is shared,
 * not per request) and the same request is repeated with a growing wait before the job is given back.
 */
@Component
@Slf4j
public class GeminiImageProvider implements ImageProvider {

    private static final List<String> HARM_CATEGORIES = List.of(
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_DANGEROUS_CONTENT",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_HARASSMENT");

    /** Waiting, as an interface so a test does not have to wait for real. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Function<ImageRequest, GenerateContentResponse> call;
    private final StorybookProperties properties;
    private final Sleeper sleeper;
    private final Semaphore gate;
    /** When the last 429 told us to hold off until (epoch millis); every request honours it. */
    private final AtomicLong pausedUntil = new AtomicLong();

    @Autowired
    public GeminiImageProvider(Client vertexGenAiClient, StorybookProperties properties) {
        this(request -> vertexGenAiClient.models.generateContent(
                request.model(), List.of(buildContent(request)), buildConfig(properties.getImage())),
                properties, Thread::sleep);
    }

    GeminiImageProvider(Function<ImageRequest, GenerateContentResponse> call, StorybookProperties properties, Sleeper sleeper) {
        this.call = call;
        this.properties = properties;
        this.sleeper = sleeper;
        this.gate = new Semaphore(Math.max(1, properties.getImage().getMaxConcurrentCalls()), true);
    }

    @Override
    public ImageResult generate(ImageRequest request) {
        try {
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageGenerationException("Interrupted while waiting for a free image request slot", true, e);
        }
        try {
            return generateWithRateLimitRetries(request);
        } finally {
            gate.release();
        }
    }

    private ImageResult generateWithRateLimitRetries(ImageRequest request) {
        int retries = Math.max(0, properties.getImage().getRateLimitRetries());
        for (int attempt = 0; ; attempt++) {
            waitForSharedPause();
            try {
                return generateOnce(request);
            } catch (RateLimitedException limited) {
                if (attempt >= retries) {
                    throw new ImageGenerationException("Image generation failed with HTTP 429", true, limited.getCause());
                }
                long wait = backoffMillis(attempt);
                pauseEveryone(wait);
                log.warn("Image provider answered HTTP 429; waiting {} s before repeating the request ({}/{})",
                        wait / 1000, attempt + 1, retries);
            }
        }
    }

    /** The first wait, doubled on each repeat, with a random spread of 50 to 100% so requests do not retry in lockstep. */
    long backoffMillis(int attempt) {
        long base = Math.max(1, properties.getImage().getRateLimitBackoff().toMillis());
        long full = base * (1L << Math.min(attempt, 10));
        return full / 2 + ThreadLocalRandom.current().nextLong(full / 2 + 1);
    }

    private void pauseEveryone(long millis) {
        long until = System.currentTimeMillis() + millis;
        pausedUntil.accumulateAndGet(until, Math::max);
    }

    private void waitForSharedPause() {
        long remaining = pausedUntil.get() - System.currentTimeMillis();
        if (remaining > 0) {
            try {
                sleeper.sleep(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ImageGenerationException("Interrupted while waiting out an image rate limit", true, e);
            }
        }
    }

    /** A 429 from the provider, carried to the retry loop. */
    private static final class RateLimitedException extends RuntimeException {
        RateLimitedException(Throwable cause) {
            super(cause);
        }
    }

    private ImageResult generateOnce(ImageRequest request) {
        long started = System.nanoTime();
        GenerateContentResponse response = null;
        boolean authError = false;
        try {
            response = call.apply(request);
        } catch (ApiException e) {
            if (e.code() == 429) {
                throw new RateLimitedException(e);
            }
            if (e.code() == 401 || e.code() == 403 || e.code() == 404) {
                authError = true;
                log.error("Vertex AI model access/auth failed (HTTP {}): {}", e.code(), e.message());
            } else {
                boolean retryable = e.code() >= 500;
                throw new ImageGenerationException("Image generation failed with HTTP " + e.code(), retryable, e);
            }
        } catch (RuntimeException e) {
            Throwable curr = e;
            while (curr != null) {
                String msg = curr.getMessage();
                if (msg != null && (msg.contains("invalid_grant") || msg.contains("OAuth") || msg.contains("credentials"))) {
                    authError = true;
                    break;
                }
                curr = curr.getCause();
            }
            if (!authError) {
                throw new ImageGenerationException("Image generation failed", true, e);
            }
            log.error("Vertex AI OAuth authentication failed (invalid_grant)");
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        if (authError || response == null) {
            // Never hand back a stand-in picture: a placeholder would ship in a paid book looking like a real illustration.
            throw new ImageGenerationException("Image provider authentication or model access failed; check the GCP credentials and image model names", false, null);
        }

        List<Part> parts = response.parts();
        if (parts != null) {
            for (Part part : parts) {
                if (part.inlineData().isPresent() && part.inlineData().get().data().isPresent()) {
                    String mime = part.inlineData().get().mimeType().orElse("image/png");
                    return new ImageResult(part.inlineData().get().data().get(), mime, request.model(), latencyMs);
                }
            }
        }
        // Usually a safety block. Retrying is worthwhile: a harmless children's scene is often
        // accepted on a second attempt, and the retry count is bounded by the caller.
        String finish = "unknown";
        try {
            if (response.candidates().isPresent() && !response.candidates().get().isEmpty()) {
                finish = response.candidates().get().get(0).finishReason().map(Object::toString).orElse("unknown");
            }
        } catch (Exception ignored) {}
        throw new ImageGenerationException("No image returned (finishReason=" + finish + ")", true, null);
    }

    static GenerateContentConfig buildConfig(StorybookProperties.Image cfg) {
        List<SafetySetting> safety = new ArrayList<>();
        for (String category : HARM_CATEGORIES) {
            safety.add(SafetySetting.builder().category(category).threshold("BLOCK_LOW_AND_ABOVE").build());
        }
        return GenerateContentConfig.builder()
                .responseModalities(List.of("IMAGE"))
                .imageConfig(ImageConfig.builder()
                        .aspectRatio(cfg.getAspectRatio())
                        .imageSize(cfg.getImageSize())
                        .build())
                .candidateCount(1)
                .safetySettings(safety)
                .build();
    }

    static Content buildContent(ImageRequest request) {
        List<Part> parts = new ArrayList<>();
        for (ReferenceImage reference : request.references()) {
            parts.add(Part.fromBytes(reference.bytes(), reference.mimeType()));
        }
        parts.add(Part.fromText(request.prompt()));
        return Content.builder().role("user").parts(parts).build();
    }
}
