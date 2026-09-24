package com.doova.ktab.features.studio.client.dto;

/**
 * One character's narration timing within a chapter's audio. Character-level, not word-level —
 * word timings are derived from these later (docs/ocr_engine_v3.md, Phase 3.6), the same way
 * {@code TtsAlignmentMapper} already derives them for the live WebSocket TTS path.
 */
public record CharacterTiming(String character, double startSec, double endSec) {
}
