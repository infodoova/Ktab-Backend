package com.doova.ktab.features.ocr.sqs;

import com.amazonaws.services.sqs.model.Message;

import java.util.List;

public interface OcrQueueService {

    int publishBookPages(Long bookId);

    void publishMessage(OcrPageMessage message);

    List<Message> receiveMessages();

    OcrPageMessage parseMessage(Message message);

    void deleteMessage(Message message);

    void moveToDeadLetter(OcrPageMessage message, String errorReason);

    boolean isEnabled();
}
