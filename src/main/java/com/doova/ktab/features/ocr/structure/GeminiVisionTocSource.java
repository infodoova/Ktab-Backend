package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class GeminiVisionTocSource implements TocSource {

    private final ChatModel chatModel;
    private final BookPageRepository pageRepository;
    private final S3OcrStorageService s3;
    private final OcrProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiVisionTocSource(
            @Qualifier("ocrGeminiModel") ChatModel chatModel,
            BookPageRepository pageRepository,
            S3OcrStorageService s3,
            OcrProperties properties
    ) {
        this.chatModel = chatModel;
        this.pageRepository = pageRepository;
        this.s3 = s3;
        this.properties = properties;
    }

    private static final String TOC_PROMPT = """
            Extract the Table of Contents (فهرس المحتويات / الموضوعات) from the provided image(s).
            Identify all section entries, hierarchy levels, division labels (e.g. الباب, الفصل, المبحث),
            ordinals, printed page numbers, and semantic section types.

            Return ONLY valid JSON matching this schema:
            {
              "entries": [
                {
                  "title": "الفصل الأول: مدخل عام",
                  "divisionLabel": "الفصل",
                  "ordinal": 1,
                  "level": 1,
                  "printedPageLabel": "١٥",
                  "sectionType": "CHAPTER"
                }
              ]
            }
            """;

    @Override
    public StructureSource source() {
        return StructureSource.TOC_VISION;
    }

    @Override
    public Optional<RawToc> extract(Long bookId) {
        try {
            List<BookPage> tocPages = pageRepository.findByBookIdAndPageKindOrderByPageNumberAsc(bookId, PageKind.TOC);
            if (tocPages.isEmpty()) {
                log.debug("No TOC pages detected for bookId={}", bookId);
                return Optional.empty();
            }

            int maxPages = properties.getToc().getMaxPages();
            List<BookPage> targetPages = tocPages.size() > maxPages ? tocPages.subList(0, maxPages) : tocPages;

            List<Media> mediaList = new ArrayList<>();
            for (BookPage page : targetPages) {
                String key = String.format("books/%d/pages/page-%04d.png", bookId, page.getPageNumber());
                byte[] bytes = s3.getPageBytes(key);
                mediaList.add(new Media(
                        MimeType.valueOf("image/png"),
                        new ByteArrayResource(bytes) {
                            @Override
                            public String getFilename() {
                                return "toc-page.png";
                            }
                        }
                ));
            }

            UserMessage message = UserMessage.builder()
                    .text(TOC_PROMPT)
                    .media(mediaList)
                    .build();

            ChatResponse response = chatModel.call(new Prompt(List.of(message)));
            if (response == null || response.getResult() == null) {
                return Optional.empty();
            }

            String output = response.getResult().getOutput().getText().trim();
            return parseTocJson(output);

        } catch (Exception e) {
            log.warn("Gemini vision TOC extraction failed for bookId={}: {}", bookId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RawToc> parseTocJson(String output) {
        String clean = output.trim();
        if (clean.startsWith("```json")) clean = clean.substring(7);
        else if (clean.startsWith("```")) clean = clean.substring(3);
        if (clean.endsWith("```")) clean = clean.substring(0, clean.length() - 3);
        clean = clean.trim();

        int first = clean.indexOf('{');
        int last = clean.lastIndexOf('}');
        if (first >= 0 && last > first) {
            clean = clean.substring(first, last + 1);
        }

        try {
            JsonNode root = objectMapper.readTree(clean);
            JsonNode entriesNode = root.path("entries");
            if (!entriesNode.isArray() || entriesNode.isEmpty()) {
                return Optional.empty();
            }

            List<RawToc.RawTocEntry> entries = new ArrayList<>();
            for (JsonNode node : entriesNode) {
                String title = node.path("title").asText("");
                if (title.isBlank()) continue;

                String div = node.has("divisionLabel") && !node.path("divisionLabel").isNull()
                        ? node.path("divisionLabel").asText()
                        : SectionClassifier.extractDivisionLabel(title).orElse(null);

                Integer ord = node.has("ordinal") && !node.path("ordinal").isNull()
                        ? node.path("ordinal").asInt()
                        : SectionClassifier.extractOrdinal(title).orElse(null);

                int level = node.path("level").asInt(1);
                String printed = node.path("printedPageLabel").asText("");

                SectionType type;
                try {
                    type = SectionType.valueOf(node.path("sectionType").asText("OTHER").toUpperCase());
                } catch (IllegalArgumentException e) {
                    type = SectionClassifier.classify(title);
                }

                entries.add(new RawToc.RawTocEntry(title, div, ord, level, printed, type));
            }

            return entries.isEmpty() ? Optional.empty() : Optional.of(new RawToc(entries));
        } catch (Exception e) {
            log.warn("Failed to parse TOC JSON from Gemini: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
