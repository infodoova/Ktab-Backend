package com.doova.ktab.features.extraction.pdf;

/** The PDF cannot be extracted; callers map it to a 422 (endpoint) or a failed book (batch job). */
public class PdfRejectedException extends RuntimeException {

    public enum Reason { EMPTY, NOT_PDF, TOO_LARGE, CORRUPTED, ENCRYPTED, NO_TEXT_LAYER }

    private final Reason reason;

    public PdfRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
