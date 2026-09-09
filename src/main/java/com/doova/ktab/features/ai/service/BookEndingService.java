package com.doova.ktab.features.ai.service;

import com.doova.ktab.features.ai.dto.request.GenerateEndingCommand;

public interface BookEndingService {

    String generateEnding(GenerateEndingCommand command);
}
