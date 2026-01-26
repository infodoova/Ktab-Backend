package com.doova.ktab.ocr.batch.interfaces;

@FunctionalInterface
public interface PageRenderCallback {
    void onPageRendered(int pageNumber, byte[] pngBytes);
}
