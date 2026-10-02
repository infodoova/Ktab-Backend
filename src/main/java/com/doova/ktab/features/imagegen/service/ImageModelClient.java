package com.doova.ktab.features.imagegen.service;

public interface ImageModelClient {

    record ImagePayload(byte[] bytes, String mimeType, String model) {}

    /**
     * Calls multimodal image generation model to generate image bytes.
     *
     * @param prompt      The detailed visual prompt
     * @param aspectRatio The desired ratio (e.g. "1:1", "3:4", "16:9")
     * @return Generated ImagePayload containing bytes and mimeType
     */
    ImagePayload generateImage(String prompt, String aspectRatio);
}
