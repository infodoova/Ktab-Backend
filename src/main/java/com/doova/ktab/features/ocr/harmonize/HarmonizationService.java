package com.doova.ktab.features.ocr.harmonize;

import java.util.Optional;

public interface HarmonizationService {

    Optional<String> harmonize(String prevTail, String rawText, String nextHead, String sectionTitle);
}
