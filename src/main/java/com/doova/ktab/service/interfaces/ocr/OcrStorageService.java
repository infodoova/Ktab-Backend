package com.doova.ktab.service.interfaces.ocr;

import com.doova.ktab.ocr.batch.PageItem;

import java.io.InputStream;
import java.util.List;

public interface OcrStorageService {

    String putBookPdf(Long bookId, InputStream pdf, long size);

    InputStream getStream(String key);

    void uploadPagePng(Long bookId, int page, byte[] bytes);

    void moveToDeadLetter(String sourceKey, Long bookId, int page, String reason);

    void deletePages(Long bookId);

    List<String> listPageKeys(Long bookId);

    List<PageItem> listPages(Long bookId);
}