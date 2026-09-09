package com.doova.ktab.features.ai.service;

import com.doova.ktab.features.ai.dto.request.ConclusionRequest;
import reactor.core.publisher.Flux;

import java.io.IOException;

public interface ConclusionGeneratorService {

    Flux<String> streamConclusion(ConclusionRequest request);

    String fetchConclusion(ConclusionRequest request) throws IOException;
}
