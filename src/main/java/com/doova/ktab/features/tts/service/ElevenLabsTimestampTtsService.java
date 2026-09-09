package com.doova.ktab.features.tts.service;

import com.doova.ktab.features.tts.dto.TtsStreamChunk;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Consumer;

public interface ElevenLabsTimestampTtsService {

    Mono<TtsStreamChunk> streamWithTimestamps(String text, String voiceId, String prevText, String nextText, List<String> previousRequestIds, Consumer<String> onRequestId);
}
