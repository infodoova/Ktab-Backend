package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.features.talktobook.dto.GuardrailDecision;
import com.doova.ktab.features.talktobook.dto.RetrievedContext;
import com.doova.ktab.features.talktobook.dto.request.TalkToBookRequest;
import com.doova.ktab.features.talktobook.dto.response.BookCitation;
import com.doova.ktab.features.talktobook.dto.response.TalkToBookResponse;
import com.doova.ktab.features.talktobook.enums.QueryIntent;
import com.doova.ktab.features.talktobook.model.BookAgentRecord;
import com.doova.ktab.features.talktobook.service.impl.TalkToBookServiceImpl;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.doova.ktab.features.talktobook.exception.InvalidBookCitationsException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TalkToBookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookAgentRecordCacheService cacheService;

    @Mock
    private QuestionGuardrailService guardrailService;

    @Mock
    private BookKnowledgeRetrieverService knowledgeRetriever;

    @Mock
    private BookWebSearchService webSearchService;

    @Mock
    private BookIdentityVerifierService identityVerifier;

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private EmbeddingModel embeddingModel;

    private TalkToBookServiceImpl talkToBookService;
    private ObjectMapper objectMapper;
    private Book testBook;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        talkToBookService = new TalkToBookServiceImpl(
                bookRepository,
                cacheService,
                guardrailService,
                knowledgeRetriever,
                webSearchService,
                identityVerifier,
                chatModel,
                embeddingModel,
                objectMapper
        );

        testBook = new Book();
        testBook.setId(1L);
        testBook.setTitle("The Prophet");
        testBook.setCustomAuthorName("Kahlil Gibran");
        when(cacheService.computeRevision(testBook)).thenReturn("revision");
    }

    @Test
    @DisplayName("askQuestion_cacheHit_returnsCachedAnswerWithoutCallingGuardrailOrLLM")
    void askQuestion_cacheHit_returnsCachedAnswerWithoutCallingGuardrailOrLLM() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash("What is love?")).thenReturn("hash123");

        BookAgentRecord cachedRecord = new BookAgentRecord();
        cachedRecord.setAnswer("Love gives naught but itself and takes naught but from itself [1].");
        cachedRecord.setCitations(List.of(new BookCitation(1, "Love gives naught but itself")));
        cachedRecord.setCitedPages(List.of(12));
        cachedRecord.setCountUsed(5);

        when(cacheService.findSimilar(1L, "hash123", null, "revision")).thenReturn(Optional.of(cachedRecord));

        TalkToBookRequest request = new TalkToBookRequest("What is love?");
        TalkToBookResponse response = talkToBookService.askQuestion(1L, request, 100L);

        assertThat(response.cached()).isTrue();
        assertThat(response.source()).isEqualTo("CACHED");
        assertThat(response.answer()).isEqualTo(cachedRecord.getAnswer());
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).snippet()).isEqualTo("Love gives naught but itself");
        assertThat(response.citedPages()).isNull();
        assertThat(response.hitCount()).isEqualTo(5);

        verifyNoInteractions(guardrailService);
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("askQuestion_cachedLegacyAnswerWithPageLabel_sanitizesPageLabelsCleanly")
    void askQuestion_cachedLegacyAnswerWithPageLabel_sanitizesPageLabelsCleanly() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash("Who is Santiago?")).thenReturn("hashLegacy");

        BookAgentRecord cachedRecord = new BookAgentRecord();
        cachedRecord.setAnswer("سانتياغو راعي أندلسي [صفحة 15] يبحث عن أسطورته الشخصية [صفحة 20-22] [1].");
        cachedRecord.setCitations(List.of(new BookCitation(1, "سانتياغو راعي أندلسي")));
        cachedRecord.setCountUsed(3);

        when(cacheService.findSimilar(1L, "hashLegacy", null, "revision")).thenReturn(Optional.of(cachedRecord));

        TalkToBookRequest request = new TalkToBookRequest("Who is Santiago?");
        TalkToBookResponse response = talkToBookService.askQuestion(1L, request, 100L);

        assertThat(response.answer()).isEqualTo("سانتياغو راعي أندلسي يبحث عن أسطورته الشخصية [1].");
        assertThat(response.citedPages()).isNull();
    }

    @Test
    @DisplayName("askQuestion_exactMatchMissSemanticMatchHit_returnsCachedAnswer")
    void askQuestion_exactMatchMissSemanticMatchHit_returnsCachedAnswer() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash("summarize tigtr th bok")).thenReturn("hashTypo");
        when(cacheService.findSimilar(1L, "hashTypo", null, "revision")).thenReturn(Optional.empty());

        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed("summarize tigtr th bok")).thenReturn(mockVector);

        BookAgentRecord semanticRecord = new BookAgentRecord();
        semanticRecord.setAnswer("This is the book summary [1].");
        semanticRecord.setCitations(List.of(new BookCitation(1, "The book summary passage")));
        semanticRecord.setCountUsed(4);

        when(cacheService.findSimilar(1L, null, "summarize tigtr th bok", List.of(0.1f, 0.2f, 0.3f), "revision"))
                .thenReturn(Optional.of(semanticRecord));

        TalkToBookRequest request = new TalkToBookRequest("summarize tigtr th bok");
        TalkToBookResponse response = talkToBookService.askQuestion(1L, request, 100L);

        assertThat(response.cached()).isTrue();
        assertThat(response.source()).isEqualTo("CACHED");
        assertThat(response.answer()).isEqualTo("This is the book summary [1].");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.hitCount()).isEqualTo(4);

        verifyNoInteractions(guardrailService);
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("askQuestion_guardrailRefuses_returnsOffTopicResponseImmediately")
    void askQuestion_guardrailRefuses_returnsOffTopicResponseImmediately() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash(any())).thenReturn("hash456");
        when(cacheService.findSimilar(1L, "hash456", null, "revision")).thenReturn(Optional.empty());

        when(guardrailService.evaluate(testBook, "How do I build a PC?"))
                .thenReturn(GuardrailDecision.refuse("This question is not related to The Prophet."));

        TalkToBookRequest request = new TalkToBookRequest("How do I build a PC?");
        TalkToBookResponse response = talkToBookService.askQuestion(1L, request, 100L);

        assertThat(response.cached()).isFalse();
        assertThat(response.source()).isEqualTo("REJECTED_OFF_TOPIC");
        assertThat(response.answer()).contains("This question is not related");
        assertThat(response.citations()).isEmpty();
        assertThat(response.citedPages()).isNull();

        verifyNoInteractions(knowledgeRetriever);
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("askQuestion_pinpointRagWithVerbatimCitations_parsesCitationsAndOmitsPageNumbers")
    void askQuestion_pinpointRagWithVerbatimCitations_parsesCitationsAndOmitsPageNumbers() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash(any())).thenReturn("hashPinpoint");
        when(cacheService.findSimilar(1L, "hashPinpoint", null, "revision")).thenReturn(Optional.empty());

        when(guardrailService.evaluate(eq(testBook), any()))
                .thenReturn(GuardrailDecision.allow(QueryIntent.PINPOINT));

        when(knowledgeRetriever.retrievePinpointContext(eq(1L), any()))
                .thenReturn(new RetrievedContext("شهادة سياسية من الداخل عن تجربة محمد جواد ظريف في إدارة السياسة الخارجية. ظريف دبلوماسياً عاد من تقاعد قسري فرضته الاستقطابات السياسية", List.of(15, 16)));

        String llmJsonOutput = """
                {
                  "answer": "يقدّم الكتاب شهادة سياسية عن تجربة محمد جواد ظريف [1]، حيث يظهر كدبلوماسي عاد من تقاعد قسري [2]...",
                  "citations": [
                    {
                      "id": 1,
                      "snippet": "شهادة سياسية من الداخل عن تجربة محمد جواد ظريف في إدارة السياسة الخارجية"
                    },
                    {
                      "id": 2,
                      "snippet": "ظريف دبلوماسياً عاد من تقاعد قسري فرضته الاستقطابات السياسية"
                    }
                  ]
                }
                """;

        ChatResponse mockChatResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage assistantMsg =
                new org.springframework.ai.chat.messages.AssistantMessage(llmJsonOutput);

        when(mockGeneration.getOutput()).thenReturn(assistantMsg);
        when(mockChatResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        TalkToBookRequest request = new TalkToBookRequest("من هو محمد جواد ظريف؟");
        TalkToBookResponse response = talkToBookService.askQuestion(1L, request, 100L);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getContents();
        assertThat(promptText)
                .contains("Citations [n] are strictly for reader text navigation")
                .contains("CURRENT question")
                .contains("only citations actually referenced in the answer")
                .contains("Do not force unrelated quotations");

        assertThat(response.source()).isEqualTo("INTERNAL_RAG");
        assertThat(response.answer()).startsWith("يقدّم الكتاب شهادة سياسية");
        assertThat(response.citations()).hasSize(2);
        assertThat(response.citations().get(0).id()).isEqualTo(1);
        assertThat(response.citations().get(0).snippet()).isEqualTo("شهادة سياسية من الداخل عن تجربة محمد جواد ظريف في إدارة السياسة الخارجية");
        assertThat(response.citations().get(1).id()).isEqualTo(2);
        assertThat(response.citations().get(1).snippet()).isEqualTo("ظريف دبلوماسياً عاد من تقاعد قسري فرضته الاستقطابات السياسية");
        // Page numbers are never returned in response payload
        assertThat(response.citedPages()).isNull();

        verify(cacheService).saveRecord(
                eq(testBook),
                eq("من هو محمد جواد ظريف؟"),
                eq("hashPinpoint"),
                isNull(),
                eq(response.answer()),
                eq(response.citations()),
                eq(List.of(15, 16)),
                eq(false),
                eq("revision")
        );
    }

    @Test
    @DisplayName("askQuestion_macroIntentVerifiedWeb_returnsWebAugmentedAnswerWithBookCitations")
    void askQuestion_macroIntentVerifiedWeb_returnsWebAugmentedAnswer() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash(any())).thenReturn("hashMacro");
        when(cacheService.findSimilar(1L, "hashMacro", null, "revision")).thenReturn(Optional.empty());

        when(guardrailService.evaluate(eq(testBook), any()))
                .thenReturn(GuardrailDecision.allow(QueryIntent.MACRO_SUMMARY));

        when(webSearchService.searchBookOverview(testBook)).thenReturn(Optional.of("The Prophet contains 26 poetic essays."));
        when(identityVerifier.verifyBookIdentity(eq(testBook), any())).thenReturn(true);
        when(knowledgeRetriever.retrieveMacroContext(1L)).thenReturn(new RetrievedContext("TOC: On Love, On Marriage", List.of(1, 2)));

        String llmJsonOutput = """
                ```json
                {
                  "answer": "The Prophet covers deep life topics [1].",
                  "citations": [
                    {
                      "id": 1,
                      "snippet": "TOC: On Love, On Marriage"
                    }
                  ]
                }
                ```
                """;

        ChatResponse mockChatResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage assistantMsg =
                new org.springframework.ai.chat.messages.AssistantMessage(llmJsonOutput);

        when(mockGeneration.getOutput()).thenReturn(assistantMsg);
        when(mockChatResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        TalkToBookRequest request = new TalkToBookRequest("Summarize this book");
        TalkToBookResponse response = talkToBookService.askQuestion(1L, request, 100L);

        assertThat(response.source()).isEqualTo("WEB_AUGMENTED");
        assertThat(response.answer()).isEqualTo("The Prophet covers deep life topics [1].");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).snippet()).isEqualTo("TOC: On Love, On Marriage");
        assertThat(response.citedPages()).isNull();
        verify(cacheService).saveRecord(eq(testBook), eq("Summarize this book"), eq("hashMacro"), isNull(), any(), any(), any(), eq(true), eq("revision"));
    }

    @Test
    @DisplayName("askQuestion_citationFromBookDescription_rejectedAndRetried")
    void askQuestion_citationFromBookDescription_rejectedAndRetried() {
        testBook.setDescription("Official book catalog description blurb.");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash(any())).thenReturn("hashDescTest");
        when(cacheService.findSimilar(1L, "hashDescTest", null, "revision")).thenReturn(Optional.empty());

        when(guardrailService.evaluate(eq(testBook), any()))
                .thenReturn(GuardrailDecision.allow(QueryIntent.PINPOINT));

        when(knowledgeRetriever.retrievePinpointContext(eq(1L), any()))
                .thenReturn(new RetrievedContext("Actual book text passage from page five.", List.of(5)));

        // First attempt mistakenly quotes the book description; retry correctly quotes book text
        String invalidLlmOutput = """
                {
                  "answer": "Summary using description [1].",
                  "citations": [
                    {
                      "id": 1,
                      "snippet": "Official book catalog description blurb."
                    }
                  ]
                }
                """;
        String validLlmOutput = """
                {
                  "answer": "Summary using book pages [1].",
                  "citations": [
                    {
                      "id": 1,
                      "snippet": "Actual book text passage from page five."
                    }
                  ]
                }
                """;

        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse(invalidLlmOutput), chatResponse(validLlmOutput));

        TalkToBookRequest request = new TalkToBookRequest("What is on page 5?");
        TalkToBookResponse response = talkToBookService.askQuestion(1L, request, 100L);

        assertThat(response.answer()).isEqualTo("Summary using book pages [1].");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).snippet()).isEqualTo("Actual book text passage from page five.");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("askQuestion_llmOutputsPlainText_retriesThenFailsWithoutCaching")
    void askQuestion_llmOutputsPlainText_retriesThenFailsWithoutCaching() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash(any())).thenReturn("hashFallback");
        when(cacheService.findSimilar(1L, "hashFallback", null, "revision")).thenReturn(Optional.empty());

        when(guardrailService.evaluate(eq(testBook), any()))
                .thenReturn(GuardrailDecision.allow(QueryIntent.PINPOINT));

        when(knowledgeRetriever.retrievePinpointContext(eq(1L), any()))
                .thenReturn(new RetrievedContext("Context text", List.of(5)));

        String plainTextOutput = "Here is a direct answer without JSON formatting.";

        ChatResponse mockChatResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage assistantMsg =
                new org.springframework.ai.chat.messages.AssistantMessage(plainTextOutput);

        when(mockGeneration.getOutput()).thenReturn(assistantMsg);
        when(mockChatResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        TalkToBookRequest request = new TalkToBookRequest("Explain something");
        assertThatThrownBy(() -> talkToBookService.askQuestion(1L, request, 100L))
                .isInstanceOf(InvalidBookCitationsException.class);
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(cacheService, never()).saveRecord(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"answer\":\"Summary [1]\"}",
            "{\"answer\":\"Summary [1]\",\"citations\":[]}",
            "{\"answer\":\"Summary [2]\",\"citations\":[{\"id\":1,\"snippet\":\"Evidence from the book\"}]}",
            "{\"answer\":\"Summary [1]\",\"citations\":[{\"id\":1,\"snippet\":\"Invented evidence\"}]}",
            "{\"answer\":\"Summary\",\"citations\":[{\"id\":1,\"snippet\":\"Evidence from the book\"}]}",
            "{\"answer\":\"Summary [1]\",\"citations\":[{\"snippet\":\"Evidence from the book\"}]}",
            "{\"answer\":\"Summary [1]\",\"citations\":[{\"id\":1,\"snippet\":\"Evidence from the book\"},{\"id\":1,\"snippet\":\"Evidence from the book\"}]}",
            "null",
            "Summary [1]",
            "{\"answer\":\"Truncated answer [1]"
    })
    void askQuestion_invalidSummary_retriesAndCachesOnlyVerifiedAnswer(String invalidOutput) {
        stubSummaryRetrieval();
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(invalidOutput), chatResponse(validSummary()));

        TalkToBookResponse response = talkToBookService.askQuestion(1L, new TalkToBookRequest("Summarize"), 100L);

        assertThat(response.answer()).isEqualTo("Summary [1]");
        assertThat(response.citations()).containsExactly(new BookCitation(1, "Evidence from the book"));
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(cacheService).saveRecord(eq(testBook), eq("Summarize"), eq("hash"), isNull(),
                eq(response.answer()), eq(response.citations()), eq(List.of(1)), eq(false), eq("revision"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "empty", "mismatch"})
    void askQuestion_invalidCachedSummary_regeneratesWithCitations(String kind) {
        stubSummaryRetrieval();
        BookAgentRecord cached = new BookAgentRecord();
        cached.setAnswer("Old summary [1]");
        cached.setCitations(switch (kind) {
            case "null" -> null;
            case "empty" -> List.of();
            default -> List.of(new BookCitation(2, "Evidence from the book"));
        });
        when(cacheService.findSimilar(1L, "hash", null, "revision")).thenReturn(Optional.of(cached));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validSummary()));

        TalkToBookResponse response = talkToBookService.askQuestion(1L, new TalkToBookRequest("Summarize"), 100L);

        assertThat(response.cached()).isFalse();
        assertThat(response.answer()).isEqualTo("Summary [1]");
        assertThat(response.citations()).containsExactly(new BookCitation(1, "Evidence from the book"));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void askQuestion_summaryWithoutEvidence_failsWithoutCaching() {
        stubSummaryRetrieval();
        when(knowledgeRetriever.retrieveMacroContext(1L)).thenReturn(new RetrievedContext("", List.of()));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validSummary()));

        assertThatThrownBy(() -> talkToBookService.askQuestion(1L, new TalkToBookRequest("Summarize"), 100L))
                .isInstanceOf(InvalidBookCitationsException.class);
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(cacheService, never()).saveRecord(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void askQuestion_truncatedOutput_retriesWithSchemaAndShorterAnswerInstruction() throws Exception {
        stubSummaryRetrieval();
        ChatResponse truncated = new ChatResponse(List.of(new Generation(
                new org.springframework.ai.chat.messages.AssistantMessage(validSummary()),
                org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("length").build())));
        when(chatModel.call(any(Prompt.class))).thenReturn(truncated, chatResponse(validSummary()));

        TalkToBookResponse response = talkToBookService.askQuestion(1L, new TalkToBookRequest("Summarize"), 100L);

        assertThat(response.citations()).containsExactly(new BookCitation(1, "Evidence from the book"));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        for (Prompt sent : prompts.getAllValues()) {
            var options = (org.springframework.ai.openai.OpenAiChatOptions) sent.getOptions();
            var format = options.getResponseFormat();
            assertThat(format.getType()).isEqualTo(org.springframework.ai.openai.api.ResponseFormat.Type.JSON_SCHEMA);
            assertThat(format.getJsonSchema().getStrict()).isTrue();
            var schema = objectMapper.valueToTree(format.getJsonSchema().getSchema());
            assertThat(schema.get("required").toString()).isEqualTo("[\"answer\",\"citations\"]");
            assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
            assertThat(schema.at("/properties/citations/items/required").toString()).isEqualTo("[\"id\",\"snippet\"]");
            assertThat(schema.at("/properties/citations/items/additionalProperties").asBoolean()).isFalse();
        }
        assertThat(prompts.getAllValues().get(1).getContents()).contains("at most three short paragraphs");
        verify(cacheService).saveRecord(eq(testBook), eq("Summarize"), eq("hash"), isNull(),
                eq(response.answer()), eq(response.citations()), eq(List.of(1)), eq(false), eq("revision"));
    }

    @Test
    @DisplayName("askQuestion_arabicWithTatweelAndTashkeel_successfullyValidatesCitation")
    void askQuestion_arabicWithTatweelAndTashkeel_successfullyValidatesCitation() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash(any())).thenReturn("hash-arabic");
        when(cacheService.findSimilar(eq(1L), any(), isNull(), eq("revision"))).thenReturn(Optional.empty());
        when(guardrailService.evaluate(eq(testBook), any())).thenReturn(GuardrailDecision.allow(QueryIntent.PINPOINT));

        String arabicContextWithKashida = "عندما ينــــــــــــام\nالعــــــــــــــــــــــالم\nقصص، كلمات، وجروح فلسطينيّة مفتوحة";
        when(knowledgeRetriever.retrievePinpointContext(eq(1L), any()))
                .thenReturn(new RetrievedContext(arabicContextWithKashida, List.of(3)));

        String arabicAnswer = "{\"answer\":\"يتناول قضايا الوطن الجريح [1].\",\"citations\":[{\"id\":1,\"snippet\":\"عندما ينام العالم\"}]}";
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(arabicAnswer));

        TalkToBookResponse response = talkToBookService.askQuestion(1L, new TalkToBookRequest("عن ماذا يتحدث الكتاب؟"), 100L);

        assertThat(response.answer()).isEqualTo("يتناول قضايا الوطن الجريح [1].");
        assertThat(response.citations()).containsExactly(new BookCitation(1, "عندما ينام العالم"));
        // Successfully validated on first attempt without triggering retry
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    private void stubSummaryRetrieval() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(cacheService.computeHash(any())).thenReturn("hash");
        when(guardrailService.evaluate(eq(testBook), any())).thenReturn(GuardrailDecision.allow(QueryIntent.MACRO_SUMMARY));
        when(webSearchService.searchBookOverview(testBook)).thenReturn(Optional.empty());
        when(knowledgeRetriever.retrieveMacroContext(1L))
                .thenReturn(new RetrievedContext("Evidence from the book", List.of(1)));
    }

    private String validSummary() {
        return "{\"answer\":\"Summary [1]\",\"citations\":[{\"id\":1,\"snippet\":\"Evidence from the book\"}]}";
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(text))));
    }
}
