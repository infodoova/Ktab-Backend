package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.features.talktobook.config.TalkToBookProperties;
import com.doova.ktab.features.talktobook.dto.response.BookCitation;
import com.doova.ktab.features.talktobook.event.model.BookAgentRecordCreatedEvent;
import com.doova.ktab.features.talktobook.model.BookAgentRecord;
import com.doova.ktab.features.talktobook.repository.BookAgentRecordRepository;
import com.doova.ktab.features.talktobook.service.impl.BookAgentRecordCacheServiceImpl;
import com.doova.ktab.model.book.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.openai.OpenAiChatModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookAgentRecordCacheServiceTest {

    @Mock
    private BookAgentRecordRepository recordRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OpenAiChatModel chatModel;

    private ObjectMapper objectMapper;
    private TalkToBookProperties properties;
    private BookAgentRecordCacheServiceImpl cacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new TalkToBookProperties();
        properties.setSimilarityThreshold(0.90);
        cacheService = new BookAgentRecordCacheServiceImpl(recordRepository, properties, eventPublisher, chatModel, objectMapper);
    }

    @Test
    @DisplayName("computeHash_normalizedQuestion_returnsStableSha256Hex")
    void computeHash_normalizedQuestion_returnsStableSha256Hex() {
        String hash1 = cacheService.computeHash("  Who is Santiago?  ");
        String hash2 = cacheService.computeHash("who is santiago");

        assertThat(hash1).isNotBlank().isEqualTo(hash2);
    }

    @Test
    @DisplayName("findSimilar_exactMatchFound_incrementsCountAndReturnsRecord")
    void findSimilar_exactMatchFound_incrementsCountAndReturnsRecord() {
        BookAgentRecord existing = new BookAgentRecord();
        existing.setCountUsed(1);
        markFresh(existing);
        existing.setAnswer("Santiago is a shepherd.");

        when(recordRepository.findByBookIdAndQuestionHash(1L, "samplehash"))
                .thenReturn(Optional.of(existing));

        Optional<BookAgentRecord> result = cacheService.findSimilar(1L, "samplehash", null, "revision");

        assertThat(result).isPresent();
        assertThat(result.get().getCountUsed()).isEqualTo(2);
        verify(recordRepository).save(existing);
    }

    @Test
    void embeddingSimilarityAloneDoesNotReuseAnAnswer() {
        BookAgentRecord candidate = new BookAgentRecord();
        markFresh(candidate);
        candidate.setQuestion("summarize chapter two");
        candidate.setQuestionEmbedding(List.of(1.0f, 0.0f));
        when(recordRepository.findByBookId(1L)).thenReturn(List.of(candidate));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(aiMatchResponse("{\"matchIndex\":null}"));

        Optional<BookAgentRecord> result = cacheService.findSimilar(
                1L, null, "summarize the whole book", List.of(1.0f, 0.0f), "revision");

        assertThat(result).isEmpty();
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveRecord_validInput_persistsAndPublishesEvent")
    void saveRecord_validInput_persistsAndPublishesEvent() {
        Book book = new Book();
        book.setId(10L);

        UUID expectedId = UUID.randomUUID();
        when(recordRepository.save(any(BookAgentRecord.class))).thenAnswer(invocation -> {
            BookAgentRecord r = invocation.getArgument(0);
            r.setId(expectedId);
            return r;
        });

        List<BookCitation> citations = List.of(new BookCitation(1, "Exact snippet here", 2));

        BookAgentRecord saved = cacheService.saveRecord(
                book, "What is the mission?", "hash123", List.of(0.1f, 0.2f),
                "To reach the Pyramids [1]", citations, List.of(1, 2), false, "revision"
        );

        assertThat(saved.getId()).isEqualTo(expectedId);
        assertThat(saved.getAnswer()).isEqualTo("To reach the Pyramids [1]");
        assertThat(saved.getCitations()).hasSize(1);
        assertThat(saved.getCitations().get(0).snippet()).isEqualTo("Exact snippet here");
        assertThat(saved.getCountUsed()).isEqualTo(1);

        ArgumentCaptor<BookAgentRecordCreatedEvent> eventCaptor = ArgumentCaptor.forClass(BookAgentRecordCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().bookId()).isEqualTo(10L);
    }

    private org.springframework.ai.chat.model.ChatResponse aiMatchResponse(String json) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(
                new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage(json))));
    }

    private void markFresh(BookAgentRecord record) {
        record.setAnswer("Current answer");
        record.setCacheRevision("revision");
        record.setAnswerGeneratedAt(Instant.now());
    }

    @Test
    void staleExactRecordsAreNotReturnedOrCounted() {
        for (String reason : List.of("legacy", "content", "blank")) {
            BookAgentRecord record = new BookAgentRecord();
            markFresh(record);
            switch (reason) {
                case "legacy" -> record.setCacheRevision(null);
                case "content" -> record.setCacheRevision("old-revision");
                case "blank" -> record.setAnswer(" ");
            }
            record.setLastAccessedAt(Instant.now());
            when(recordRepository.findByBookIdAndQuestionHash(1L, "hash")).thenReturn(Optional.of(record));
            assertThat(cacheService.findSimilar(1L, "hash", null, "revision")).as(reason).isEmpty();
            assertThat(record.getCountUsed()).isEqualTo(1);
        }
        verify(recordRepository, never()).save(any());
    }

    @Test
    void unchangedAnswerRemainsReusableRegardlessOfAge() {
        BookAgentRecord record = new BookAgentRecord();
        markFresh(record);
        Instant generatedAt = Instant.now().minus(Duration.ofDays(3650));
        record.setAnswerGeneratedAt(generatedAt);
        when(recordRepository.findByBookIdAndQuestionHash(1L, "hash")).thenReturn(Optional.of(record));

        assertThat(cacheService.findSimilar(1L, "hash", null, "revision")).contains(record);
        assertThat(record.getAnswerGeneratedAt()).isEqualTo(generatedAt);
        assertThat(record.getCountUsed()).isEqualTo(2);
    }

    @Test
    void semanticLookupSkipsStaleCandidates() {
        BookAgentRecord stale = new BookAgentRecord();
        markFresh(stale);
        stale.setCacheRevision("old");
        stale.setQuestionEmbedding(List.of(1f, 0f));
        when(recordRepository.findByBookId(1L)).thenReturn(List.of(stale));
        assertThat(cacheService.findSimilar(1L, "hash", List.of(1f, 0f), "revision")).isEmpty();
        verify(recordRepository, never()).save(any());
    }

    @Test
    void refreshUpdatesExistingRowAndCitationsWithoutCreatingEvictionEvent() {
        Book book = new Book();
        book.setId(1L);
        BookAgentRecord existing = new BookAgentRecord();
        UUID id = UUID.randomUUID();
        existing.setId(id);
        existing.setCountUsed(7);
        existing.setAnswer("Old answer");
        when(recordRepository.findByBookIdAndQuestionHash(1L, "hash")).thenReturn(Optional.of(existing));
        when(recordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<BookCitation> citations = List.of(new BookCitation(1, "New source"));
        BookAgentRecord saved = cacheService.saveRecord(book, "Question", "hash", null,
                "New answer", citations, List.of(4), true, "captured-revision");
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getAnswer()).isEqualTo("New answer");
        assertThat(saved.getCitations()).isEqualTo(citations);
        assertThat(saved.getCitedPages()).containsExactly(4);
        assertThat(saved.getCountUsed()).isEqualTo(8);
        assertThat(saved.getCacheRevision()).isEqualTo("captured-revision");
        assertThat(saved.getAnswerGeneratedAt()).isNotNull();
        assertThat(saved.isWebAugmented()).isTrue();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void revisionTracksBookContentAndResponseConfiguration() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("Book");
        when(recordRepository.computeContentRevision(1L)).thenReturn("pages-and-sections-v1");
        String original = cacheService.computeRevision(book);
        assertThat(cacheService.computeRevision(book)).isEqualTo(original);
        book.setUpdatedAt(java.time.LocalDateTime.now());
        assertThat(cacheService.computeRevision(book)).isEqualTo(original);
        book.setDescription("Updated description!");
        String metadataUpdated = cacheService.computeRevision(book);
        assertThat(metadataUpdated).isNotEqualTo(original);
        when(recordRepository.computeContentRevision(1L)).thenReturn("pages-and-sections-v2");
        String contentUpdated = cacheService.computeRevision(book);
        assertThat(contentUpdated).isNotEqualTo(metadataUpdated);
        properties.setAnswerVersion("2");
        String rulesUpdated = cacheService.computeRevision(book);
        assertThat(rulesUpdated).isNotEqualTo(contentUpdated);
        properties.setModel("new-model");
        assertThat(cacheService.computeRevision(book)).isNotEqualTo(rulesUpdated);
    }

    @Test
    void arabicSummaryRequestReusesEnglishWholeBookSummaryAfterAiReview() {
        BookAgentRecord chapter = new BookAgentRecord();
        markFresh(chapter);
        chapter.setQuestion("summarize chapter two");
        chapter.setCountUsed(10);

        BookAgentRecord wholeBook = new BookAgentRecord();
        markFresh(wholeBook);
        wholeBook.setQuestion("summarize this book");
        wholeBook.setCountUsed(2);

        when(recordRepository.findByBookId(1L)).thenReturn(List.of(chapter, wholeBook));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(aiMatchResponse("{\"matchIndex\":2}"));
        Optional<BookAgentRecord> result = cacheService.findSimilar(1L, null, "لخص", null, "revision");

        assertThat(result).contains(wholeBook);
        assertThat(wholeBook.getCountUsed()).isEqualTo(3);
        verify(recordRepository).save(wholeBook);
        verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void misspelledSummaryRequestReusesWholeBookAnswerAfterAiReview() {
        properties.setModel("gpt-6-luna");
        BookAgentRecord wholeBook = new BookAgentRecord();
        markFresh(wholeBook);
        wholeBook.setQuestion("summarize this book");
        when(recordRepository.findByBookId(1L)).thenReturn(List.of(wholeBook));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(aiMatchResponse("{\"matchIndex\":1}"));

        Optional<BookAgentRecord> result = cacheService.findSimilar(
                1L, null, "sammurize .", null, "revision");

        assertThat(result).contains(wholeBook);
        verify(recordRepository).save(wholeBook);
        ArgumentCaptor<org.springframework.ai.chat.prompt.Prompt> sent = ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(chatModel).call(sent.capture());
        var options = (org.springframework.ai.openai.OpenAiChatOptions) sent.getValue().getOptions();
        assertThat(options.getTemperature()).isNull();
        assertThat(options.getMaxCompletionTokens()).isEqualTo(256);
    }

    @Test
    void wholeBookSummaryDoesNotReuseChapterSummary() {
        BookAgentRecord chapter = new BookAgentRecord();
        markFresh(chapter);
        chapter.setQuestion("summarize chapter two");
        chapter.setQuestionEmbedding(List.of(1f, 0f));
        when(recordRepository.findByBookId(1L)).thenReturn(List.of(chapter));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(aiMatchResponse("{\"matchIndex\":null}"));

        Optional<BookAgentRecord> result = cacheService.findSimilar(1L, null, "لخص", List.of(1f, 0f), "revision");

        assertThat(result).isEmpty();
        verify(recordRepository, never()).save(any());
    }

    @Test
    void aiChecksCandidatesBeyondFirstBatch() {
        java.util.ArrayList<BookAgentRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < 31; i++) {
            BookAgentRecord record = new BookAgentRecord();
            markFresh(record);
            record.setQuestion("question " + i);
            record.setCountUsed(31 - i);
            records.add(record);
        }
        when(recordRepository.findByBookId(1L)).thenReturn(records);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(aiMatchResponse("{\"matchIndex\":null}"), aiMatchResponse("{\"matchIndex\":1}"));

        assertThat(cacheService.findSimilar(1L, null, "different wording", null, "revision"))
                .contains(records.get(30));
        verify(chatModel, times(2)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    @DisplayName("findSimilar_aiIntentMatch_incrementsCountAndReturnsRecord")
    void findSimilar_aiIntentMatch_incrementsCountAndReturnsRecord() {
        BookAgentRecord candidate = new BookAgentRecord();
        candidate.setQuestion("summarize this");
        candidate.setAnswer("Summary answer");
        candidate.setCacheRevision("revision");
        candidate.setCountUsed(2);

        when(recordRepository.findByBookIdAndQuestionHash(1L, "hashTypo")).thenReturn(Optional.empty());
        when(recordRepository.findByBookId(1L)).thenReturn(List.of(candidate));
        when(recordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        org.springframework.ai.chat.model.ChatResponse mockChatResponse = new org.springframework.ai.chat.model.ChatResponse(
                List.of(new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage("{\"matchIndex\":1}")
                ))
        );
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockChatResponse);

        Optional<BookAgentRecord> result = cacheService.findSimilar(1L, "hashTypo", "sum this book", null, "revision");

        assertThat(result).isPresent();
        assertThat(result.get().getQuestion()).isEqualTo("summarize this");
        assertThat(result.get().getCountUsed()).isEqualTo(3);
    }
}
