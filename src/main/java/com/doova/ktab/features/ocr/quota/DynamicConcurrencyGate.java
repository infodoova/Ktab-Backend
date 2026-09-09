package com.doova.ktab.features.ocr.quota;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DynamicConcurrencyGate {

    private final QuotaProvider quotaProvider;
    private final AtomicInteger current = new AtomicInteger(1);
    private volatile Semaphore semaphore = new Semaphore(1, true);

    public DynamicConcurrencyGate(QuotaProvider quotaProvider) {
        this.quotaProvider = quotaProvider;
        refresh();
    }

    public void refresh() {
        int target = Math.max(1, quotaProvider.currentMaxParallelRequests());
        int prev = current.getAndSet(target);
        if (prev == target) return;

        // Create a new semaphore with the new permits.
        // Safe because permits only gate "new" acquires; in-flight calls continue.
        semaphore = new Semaphore(target, true);
    }

    public Permit acquire() {
        refresh();
        try {
            semaphore.acquire();
            return new Permit(semaphore);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted acquiring concurrency permit", e);
        }
    }

    public int currentMax() {
        return current.get();
    }

    public record Permit(Semaphore sem) implements AutoCloseable {
        @Override
        public void close() {
            sem.release();
        }
    }
}
