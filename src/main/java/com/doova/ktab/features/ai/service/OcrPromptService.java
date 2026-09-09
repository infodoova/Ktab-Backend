package com.doova.ktab.features.ai.service;

public interface OcrPromptService {

    String getTocPrompt();

    String getOcrPrompt(String toc);

    String getHarmonizePrompt(String toc, String startingContext, String rawText);
}
