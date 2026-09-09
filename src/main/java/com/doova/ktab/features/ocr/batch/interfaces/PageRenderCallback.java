package com.doova.ktab.features.ocr.batch.interfaces;

@FunctionalInterface
public interface PageRenderCallback {
    void onPageRendered(int pageNumber, byte[] pngBytes);
}
