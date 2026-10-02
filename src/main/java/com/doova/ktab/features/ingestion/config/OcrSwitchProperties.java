package com.doova.ktab.features.ingestion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The OCR switch (KTAB_OCR_ENABLED). While false, {@code ocrJob} (and restructure/harmonize/retry) is never launched and
 * every book that would have gone to OCR goes to Ktab's own text-layer extraction instead. The OCR code stays in place.
 */
@Component
@ConfigurationProperties(prefix = "ktab.ocr")
@Getter
@Setter
public class OcrSwitchProperties {

    private boolean enabled = false;
}
