package com.doova.ktab.features.ocr.sqs;

public interface OcrQueueService {

    int publishBookPages(Long bookId);

    void publishMessage(OcrPageMessage message);

    boolean isEnabled();
}

