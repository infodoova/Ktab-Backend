package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The 429 handling and the cap on simultaneous picture requests. No real waiting happens: the sleeper only records. */
class GeminiImageProviderRateLimitTest {

    private final StorybookProperties properties = new StorybookProperties();
    private final List<Long> sleeps = Collections.synchronizedList(new ArrayList<>());

    private static ImageRequest request() {
        return new ImageRequest("gemini-3.1-flash-image", "a boy and a cat in a forest", List.of());
    }

    private static GenerateContentResponse imageResponse() {
        return GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder().role("model").parts(List.of(Part.fromBytes(new byte[]{1, 2, 3}, "image/png"))).build())
                        .build()))
                .build();
    }

    private GeminiImageProvider provider(Function<ImageRequest, GenerateContentResponse> call) {
        return new GeminiImageProvider(call, properties, sleeps::add);
    }

    /** A call that fails with the given HTTP codes in order, then succeeds. */
    private static Function<ImageRequest, GenerateContentResponse> failingWith(AtomicInteger calls, int... codes) {
        return r -> {
            int n = calls.getAndIncrement();
            if (n < codes.length) {
                throw new ApiException(codes[n], "ERROR", "simulated " + codes[n]);
            }
            return imageResponse();
        };
    }

    @Test
    void aSuccessfulCallReturnsTheImageWithoutWaiting() {
        AtomicInteger calls = new AtomicInteger();

        ImageResult result = provider(failingWith(calls)).generate(request());

        assertThat(result.bytes()).containsExactly(1, 2, 3);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void anHttp429IsRepeatedAfterWaitingAndThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        ImageResult result = provider(failingWith(calls, 429)).generate(request());

        assertThat(result.bytes()).isNotEmpty();
        assertThat(calls).hasValue(2);
        assertThat(sleeps).hasSize(1);
        // first wait: the 20 s base with a spread of 50 to 100%
        assertThat(sleeps.get(0)).isBetween(9_000L, 20_000L);
    }

    @Test
    void theWaitDoublesOnEachRepeat() {
        AtomicInteger calls = new AtomicInteger();

        provider(failingWith(calls, 429, 429, 429)).generate(request());

        assertThat(calls).hasValue(4);
        assertThat(sleeps).hasSize(3);
        assertThat(sleeps.get(0)).isBetween(9_000L, 20_000L);
        assertThat(sleeps.get(1)).isBetween(19_000L, 40_000L);
        assertThat(sleeps.get(2)).isBetween(39_000L, 80_000L);
    }

    @Test
    void afterTheConfiguredRepeatsTheJobGetsARetryableFailure() {
        properties.getImage().setRateLimitRetries(2);
        AtomicInteger calls = new AtomicInteger();
        GeminiImageProvider provider = provider(failingWith(calls, 429, 429, 429, 429, 429));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOfSatisfying(ImageGenerationException.class, e -> {
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.getMessage()).contains("HTTP 429");
                });
        assertThat(calls).hasValue(3);          // the first call and two repeats
    }

    @Test
    void repeatsCanBeTurnedOff() {
        properties.getImage().setRateLimitRetries(0);
        AtomicInteger calls = new AtomicInteger();
        GeminiImageProvider provider = provider(failingWith(calls, 429, 429));

        assertThatThrownBy(() -> provider.generate(request())).isInstanceOf(ImageGenerationException.class);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void aNonRateLimitClientErrorIsNeverRepeatedAndIsNotRetryable() {
        AtomicInteger calls = new AtomicInteger();
        GeminiImageProvider provider = provider(failingWith(calls, 400, 400));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOfSatisfying(ImageGenerationException.class, e -> assertThat(e.retryable()).isFalse());
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void aServerErrorIsRetryableButLeftToTheJobQueue() {
        AtomicInteger calls = new AtomicInteger();
        GeminiImageProvider provider = provider(failingWith(calls, 503, 503));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOfSatisfying(ImageGenerationException.class, e -> assertThat(e.retryable()).isTrue());
        assertThat(calls).hasValue(1);
    }

    @Test
    void aRateLimitMakesTheNextRequestWaitToo() {
        AtomicInteger calls = new AtomicInteger();
        GeminiImageProvider provider = provider(failingWith(calls, 429));

        provider.generate(request());            // gets the 429, waits, repeats
        int afterFirst = sleeps.size();
        provider.generate(request());            // a different request, started right after

        assertThat(afterFirst).isEqualTo(1);
        assertThat(sleeps.size()).as("the second request also held off before calling the provider").isEqualTo(2);
        assertThat(sleeps.get(1)).isGreaterThan(0L);
    }

    @Test
    void anInterruptedWaitEndsAsARetryableFailureAndKeepsTheInterruptFlag() {
        AtomicInteger calls = new AtomicInteger();
        GeminiImageProvider provider = new GeminiImageProvider(failingWith(calls, 429), properties, millis -> {
            throw new InterruptedException("stop");
        });

        try {
            assertThatThrownBy(() -> provider.generate(request()))
                    .isInstanceOfSatisfying(ImageGenerationException.class, e -> assertThat(e.retryable()).isTrue());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();               // do not leak the flag into other tests
        }
    }

    @Test
    void neverMoreRequestsInFlightThanTheConfiguredLimit() throws Exception {
        properties.getImage().setMaxConcurrentCalls(2);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger highest = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        GeminiImageProvider provider = provider(r -> {
            int now = inFlight.incrementAndGet();
            highest.accumulateAndGet(now, Math::max);
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            return imageResponse();
        });

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Future<ImageResult>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                results.add(pool.submit(() -> provider.generate(request())));
            }
            Thread.sleep(300);                  // let the first two reach the provider and the rest queue up
            assertThat(inFlight.get()).isEqualTo(2);
            release.countDown();
            for (var f : results) {
                assertThat(f.get(10, TimeUnit.SECONDS).bytes()).isNotEmpty();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(highest.get()).isEqualTo(2);
    }

    @Test
    void theDefaultsLeaveRoomUnderTheProvidersQuota() {
        StorybookProperties.Image image = new StorybookProperties.Image();

        assertThat(image.getMaxConcurrentCalls()).isEqualTo(2);
        assertThat(image.getRateLimitRetries()).isEqualTo(4);
        assertThat(image.getRateLimitBackoff()).isEqualTo(Duration.ofSeconds(20));
    }
}
