package com.doova.ktab.features.ocr.quota;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConfigQuotaProvider implements QuotaProvider {

    private final AtomicInteger maxParallel = new AtomicInteger(5);

    public void setMaxParallel(int v) {
        maxParallel.set(Math.max(1, v));
    }

    @Override
    public int currentMaxParallelRequests() {
        return maxParallel.get();
    }
}
