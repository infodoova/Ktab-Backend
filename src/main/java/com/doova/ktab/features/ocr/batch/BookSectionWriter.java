package com.doova.ktab.features.ocr.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use {@link BookPageWriter} instead. Kept for backward compatibility.
 */
@Deprecated
@Component
public class BookSectionWriter implements ItemWriter<OcrResult> {

    private final BookPageWriter delegate;

    public BookSectionWriter(BookPageWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(Chunk<? extends OcrResult> chunk) throws Exception {
        delegate.write(chunk);
    }
}
