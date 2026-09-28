package com.doova.ktab.features.talktobook.enums;

public enum QueryIntent {
    /**
     * Specific fact, scene, quote, or page-level query. Handled via internal Hybrid RAG.
     */
    PINPOINT,

    /**
     * Broad overview, full-book summary, character roster, or thematic analysis.
     */
    MACRO_SUMMARY
}
