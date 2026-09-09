package com.doova.ktab.features.tts.exception;

public class ElevenLabsQuotaExceededException extends RuntimeException {
    public ElevenLabsQuotaExceededException(String msg) {
        super(msg);
    }
}
