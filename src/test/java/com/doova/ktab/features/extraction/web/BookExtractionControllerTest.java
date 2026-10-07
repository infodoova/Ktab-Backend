package com.doova.ktab.features.extraction.web;

import com.doova.ktab.features.extraction.BookExtractionQueryService;
import com.doova.ktab.features.extraction.BookExtractionService;
import com.doova.ktab.features.extraction.dto.*;
import com.doova.ktab.features.extraction.pdf.PdfRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookExtractionControllerTest {

    private final BookExtractionService service = mock(BookExtractionService.class);
    private final BookExtractionQueryService queryService = mock(BookExtractionQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BookExtractionController(service, queryService)).build();
    }

    private static BookExtractionResult sample() {
        PageContent page = new PageContent(1, "raw", "clean", null, null, List.of("raw"), false);
        Chapter chapter = new Chapter(1, "المقدمة", 1, 1, TocEntryType.INTRODUCTION, "clean", List.of(page), List.of());
        return new BookExtractionResult(new BookMetadata("t", "a", null, null, "ar", 1),
                new StructureDetection(DetectionSource.EMBEDDED_OUTLINE, 1.0), List.of(), List.of(chapter), List.of(page), List.of());
    }

    @Test
    void aRejectedPdfIs422WithItsReason() throws Exception {
        when(service.extract(any())).thenThrow(new PdfRejectedException(PdfRejectedException.Reason.ENCRYPTED, "encrypted"));

        mvc.perform(multipart("/books/extract").file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("ENCRYPTED"))
                .andExpect(jsonPath("$.message").value("encrypted"));
    }

    @Test
    void pagesAreLeftOutUnlessAsked() throws Exception {
        when(service.extract(any())).thenReturn(sample());
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1});

        mvc.perform(multipart("/books/extract").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structureDetection.source").value("EMBEDDED_OUTLINE"))
                .andExpect(jsonPath("$.pages").isEmpty())
                .andExpect(jsonPath("$.chapters[0].text").value("clean"))
                .andExpect(jsonPath("$.chapters[0].pages").isEmpty());

        mvc.perform(multipart("/books/extract").file(file).param("includePages", "true"))
                .andExpect(jsonPath("$.pages[0].rawText").value("raw"))
                .andExpect(jsonPath("$.chapters[0].pages[0].cleanedText").value("clean"));
    }

    @Test
    void onlyAdminsAndLibrariansMayCallIt() throws Exception {
        PreAuthorize rule = BookExtractionController.class.getMethod("extract",
                org.springframework.web.multipart.MultipartFile.class, boolean.class).getAnnotation(PreAuthorize.class);
        assertThat(rule).isNotNull();
        assertThat(rule.value()).contains("ADMIN").contains("LIBRARIAN").doesNotContain("READER").doesNotContain("AUTHOR");
    }
}
