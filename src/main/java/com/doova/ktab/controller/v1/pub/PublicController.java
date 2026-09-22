package com.doova.ktab.controller.v1.pub;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.book.BookCoverResponse;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.RoleMetadataDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.pub.PublicService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/public", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Public", description = "Public (unauthenticated) catalog and metadata endpoints")
public class PublicController {

    private final PublicService publicService;
    private final MessageSource messageSource;

    @Operation(summary = "Get published book covers for the public catalog")
    @GetMapping("/books/covers")
    public ResponseEntity<ApiResponse<PageResponse<BookCoverResponse>>> getBookCovers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "18") int size
    ) {
        PageResponse<BookCoverResponse> response = publicService.getBookCovers(page, size);
        return ResponseUtils.success(response, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get registration and user roles (Author and Reader only)")
    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleMetadataDto>>> getRoles() {
        List<RoleMetadataDto> roles = publicService.getRoles();
        return ResponseUtils.success(roles, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
