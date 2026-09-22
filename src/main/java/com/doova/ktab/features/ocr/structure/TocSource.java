package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.StructureSource;

import java.util.Optional;

public interface TocSource {

    StructureSource source();

    Optional<RawToc> extract(Long bookId);
}
