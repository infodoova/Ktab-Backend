package com.doova.ktab.service.book;

import com.doova.ktab.dto.book.BookSettingsPatchRequestDto;
import com.doova.ktab.dto.book.BookStructureResponseDto;
import com.doova.ktab.dto.book.BookStructureUpdateRequestDto;

public interface BookStructureService {

    BookStructureResponseDto getStructure(Long bookId);

    BookStructureResponseDto updateStructure(Long bookId, BookStructureUpdateRequestDto request);

    void patchSettings(Long bookId, BookSettingsPatchRequestDto request);
}
