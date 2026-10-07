package com.doova.ktab.service.pub.impl;

import com.doova.ktab.dto.book.BookCoverResponse;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.RoleMetadataDto;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.service.metadata.MetadataService;
import com.doova.ktab.service.pub.PublicService;
import com.doova.ktab.utils.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicServiceImpl implements PublicService {

    private final BookService     bookService;
    private final MetadataService metadataService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookCoverResponse> getBookCovers(int page, int size) {
        log.debug("Public catalog: fetching book covers page={} size={}", page, size);
        return bookService.getBookCovers(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getTopReviewedCoverImages(int limit) {
        log.debug("Public catalog: fetching top reviewed cover images limit={}", limit);
        return bookService.getTopReviewedCoverImages(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<String> getCoverImages(int page, int size) {
        log.debug("Public catalog: fetching cover images page={} size={}", page, size);
        return bookService.getCoverImages(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleMetadataDto> getRoles() {
        log.debug("Public catalog: fetching registration roles");
        return metadataService.getRoles();
    }
}
