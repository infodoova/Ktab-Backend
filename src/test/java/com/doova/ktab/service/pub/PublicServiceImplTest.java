package com.doova.ktab.service.pub;

import com.doova.ktab.dto.book.BookCoverResponse;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.RoleMetadataDto;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.service.metadata.MetadataService;
import com.doova.ktab.service.pub.impl.PublicServiceImpl;
import com.doova.ktab.utils.pagination.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicServiceImplTest {

    @Mock
    private BookService bookService;

    @Mock
    private MetadataService metadataService;

    @InjectMocks
    private PublicServiceImpl publicService;

    // ========================================================================================
    // getBookCovers
    // ========================================================================================

    @Test
    @DisplayName("getBookCovers_delegatesToBookService")
    void getBookCovers_delegatesToBookService() {
        BookCoverResponse cover = BookCoverResponse.builder()
                .id(1L)
                .title("The Great Book")
                .coverImageUrl("https://cdn.example.com/covers/1.jpg")
                .mainGenre("Fiction")
                .build();

        PageResponse<BookCoverResponse> mockPage = new PageResponse<>(
                List.of(cover), 0, 18, 1L, 1, true
        );
        when(bookService.getBookCovers(0, 18)).thenReturn(mockPage);

        PageResponse<BookCoverResponse> result = publicService.getBookCovers(0, 18);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("The Great Book");
        verify(bookService).getBookCovers(0, 18);
    }

    // ========================================================================================
    // getRoles
    // ========================================================================================

    @Test
    @DisplayName("getRoles_delegatesToMetadataService")
    void getRoles_delegatesToMetadataService() {
        RoleMetadataDto authorRole = new RoleMetadataDto("AUTHOR", "10", "مؤلف", "Author");
        RoleMetadataDto readerRole = new RoleMetadataDto("READER", "20", "قارئ", "Reader");

        when(metadataService.getRoles()).thenReturn(List.of(authorRole, readerRole));

        List<RoleMetadataDto> result = publicService.getRoles();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).role()).isEqualTo("AUTHOR");
        assertThat(result.get(1).role()).isEqualTo("READER");
        verify(metadataService).getRoles();
    }
}
