package com.doova.ktab.ocr.web;

import com.doova.ktab.ocr.quota.ConfigQuotaProvider;
import com.doova.ktab.ocr.quota.DynamicConcurrencyGate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ocr/quotas")
public class QuotaController {

    private final ConfigQuotaProvider quota;
    private final DynamicConcurrencyGate gate;

    @GetMapping
    public Map<String, Object> get() {
        return Map.of("maxParallelRequests", quota.currentMaxParallelRequests(), "gateCurrentMax", gate.currentMax());
    }

    @PostMapping("/max-parallel")
    public ResponseEntity<Map<String, Object>> set(@RequestParam int value) {
        quota.setMaxParallel(value);
        gate.refresh();
        return ResponseEntity.ok(get());
    }
}
