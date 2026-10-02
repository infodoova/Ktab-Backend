package com.doova.ktab.features.talktobook.service.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.talktobook.dto.GuardrailDecision;
import com.doova.ktab.features.talktobook.dto.RetrievedContext;
import com.doova.ktab.features.talktobook.dto.request.TalkToBookRequest;
import com.doova.ktab.features.talktobook.dto.response.BookCitation;
import com.doova.ktab.features.talktobook.dto.response.TalkToBookResponse;
import com.doova.ktab.features.talktobook.enums.QueryIntent;
import com.doova.ktab.features.talktobook.model.BookAgentRecord;
import com.doova.ktab.features.talktobook.service.*;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.doova.ktab.features.talktobook.exception.InvalidBookCitationsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TalkToBookServiceImpl implements TalkToBookService {

    private static final Pattern CITATION_REFERENCE = Pattern.compile("\\[([0-9]+)\\]");

    private static final String ANSWER_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "answer": {"type": "string"},
                "citations": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "id": {"type": "integer"},
                      "snippet": {"type": "string"}
                    },
                    "required": ["id", "snippet"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["answer", "citations"],
              "additionalProperties": false
            }
            """;

    private final BookRepository bookRepository;
    private final BookAgentRecordCacheService cacheService;
    private final QuestionGuardrailService guardrailService;
    private final BookKnowledgeRetrieverService knowledgeRetriever;
    private final BookWebSearchService webSearchService;
    private final BookIdentityVerifierService identityVerifier;
    private final OpenAiChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    public TalkToBookServiceImpl(
            BookRepository bookRepository,
            BookAgentRecordCacheService cacheService,
            QuestionGuardrailService guardrailService,
            BookKnowledgeRetrieverService knowledgeRetriever,
            BookWebSearchService webSearchService,
            BookIdentityVerifierService identityVerifier,
            @Qualifier("talkToBookChatModel") OpenAiChatModel chatModel,
            @Qualifier("talkToBookEmbeddingModel") EmbeddingModel embeddingModel,
            ObjectMapper objectMapper) {
        this.bookRepository = bookRepository;
        this.cacheService = cacheService;
        this.guardrailService = guardrailService;
        this.knowledgeRetriever = knowledgeRetriever;
        this.webSearchService = webSearchService;
        this.identityVerifier = identityVerifier;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public TalkToBookResponse askQuestion(Long bookId, TalkToBookRequest request, Long userId) {
        // Senior Backend Rule: Do not hold a database transaction open during external AI or HTTP calls
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.READER_BOOK_NOT_FOUND));

        String rawQuestion = request.question();
        String questionHash = cacheService.computeHash(rawQuestion);

        // Capture before retrieval so concurrent edits cannot mark an old answer as current.
        String cacheRevision = cacheService.computeRevision(book);

        // Step 1: Exact Hash Cache Lookup (Tier 1)
        Optional<BookAgentRecord> cachedRecord = cacheService.findSimilar(bookId, questionHash, null, cacheRevision);
        if (cachedRecord.isPresent() && hasValidCitations(new LlmAnswerPayload(
                sanitizeAnswer(cachedRecord.get().getAnswer()), cachedRecord.get().getCitations()), null)) {
            BookAgentRecord record = cachedRecord.get();
            log.info("Serving answer from cache for book {} (hitCount: {})", bookId, record.getCountUsed());
            List<BookCitation> cachedCitations = record.getCitations() != null ? record.getCitations() : List.of();
            String cleanAnswer = sanitizeAnswer(record.getAnswer());
            return new TalkToBookResponse(
                    rawQuestion,
                    cleanAnswer,
                    cachedCitations,
                    true,
                    "CACHED",
                    record.getCountUsed()
            );
        }

        // Step 1b: Semantic Vector & AI Intent Cache Lookup (Tier 2 & Tier 3)
        List<Float> questionEmbedding = generateQuestionEmbeddingSafely(rawQuestion);
        Optional<BookAgentRecord> semanticRecord = cacheService.findSimilar(bookId, null, rawQuestion, questionEmbedding, cacheRevision);
        if (semanticRecord.isPresent() && hasValidCitations(new LlmAnswerPayload(
                sanitizeAnswer(semanticRecord.get().getAnswer()), semanticRecord.get().getCitations()), null)) {
            BookAgentRecord record = semanticRecord.get();
            log.info("Serving answer from semantic cache for book {} (hitCount: {})", bookId, record.getCountUsed());
            List<BookCitation> cachedCitations = record.getCitations() != null ? record.getCitations() : List.of();
            String cleanAnswer = sanitizeAnswer(record.getAnswer());
            return new TalkToBookResponse(
                    rawQuestion,
                    cleanAnswer,
                    cachedCitations,
                    true,
                    "CACHED",
                    record.getCountUsed()
            );
        }

        // Step 2: Strict Book-Relevance & Safety Guardrail Check
        GuardrailDecision decision = guardrailService.evaluate(book, rawQuestion);
        if (!decision.allowed()) {
            log.info("Question refused by guardrail for book {}: {}", bookId, decision.refusalReason());
            return new TalkToBookResponse(
                    rawQuestion,
                    decision.refusalReason(),
                    List.of(),
                    false,
                    "REJECTED_OFF_TOPIC",
                    0
            );
        }

        // Step 3: Dual Retrieval Routing (Pinpoint RAG vs Verified Web Augmentation)
        StringBuilder backgroundOverview = new StringBuilder();
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            backgroundOverview.append("=== نبذة تعريفية عامة عن الكتاب (للفهم والاستيعاب العام فقط - يُمنع الاقتباس منها في المراجع) ===\n")
                    .append(book.getDescription().trim())
                    .append("\n\n");
        }

        List<Integer> internalCitedPages = new ArrayList<>();
        String source;
        String citablePagesText;

        if (decision.intent() == QueryIntent.MACRO_SUMMARY) {
            log.debug("Routing to macro retrieval for book {}", bookId);
            Optional<String> webSnippet = webSearchService.searchBookOverview(book);

            if (webSnippet.isPresent() && identityVerifier.verifyBookIdentity(book, webSnippet.get())) {
                RetrievedContext internalMacro = knowledgeRetriever.retrieveMacroContext(bookId);
                backgroundOverview.append("=== نبذة موثقة من مصادر معتمدة عن الكتاب (للفهم العام فقط - يُمنع الاقتباس منها في المراجع) ===\n")
                        .append(webSnippet.get())
                        .append("\n\n");
                citablePagesText = internalMacro.contextText();
                internalCitedPages.addAll(internalMacro.citedPages());
                source = "WEB_AUGMENTED";
            } else {
                RetrievedContext internalMacro = knowledgeRetriever.retrieveMacroContext(bookId);
                citablePagesText = internalMacro.contextText();
                internalCitedPages.addAll(internalMacro.citedPages());
                source = "INTERNAL_RAG";
            }
        } else {
            log.debug("Routing to pinpoint internal RAG for book {}", bookId);
            RetrievedContext pinpointContext = knowledgeRetriever.retrievePinpointContext(bookId, rawQuestion);
            citablePagesText = pinpointContext.contextText();
            internalCitedPages.addAll(pinpointContext.citedPages());
            source = "INTERNAL_RAG";
        }

        StringBuilder fullContextBuilder = new StringBuilder();
        if (!backgroundOverview.isEmpty()) {
            fullContextBuilder.append(backgroundOverview);
        }
        fullContextBuilder.append("=== نصوص ومقتطفات معتمدة من صفحات الكتاب (جميع الاستشهادات [n] والمقتطفات يجب أن تُقتبس حصراً وحرفياً من هذا القسم) ===\n")
                .append(citablePagesText);

        String contextText = fullContextBuilder.toString().trim();

        // Step 4: ChatGPT Prompt Synthesis with Citations
        String authorName = book.getCustomAuthorName() != null ? book.getCustomAuthorName() :
                (book.getAuthor() != null ? book.getAuthor().getFirstName() + " " + book.getAuthor().getLastName() : "غير محدد");

        String systemPrompt = String.format("""
                أنت مساعد القراءة الذكي والموثوق لكتاب "%s" للكاتب/المؤلف "%s".
                مهمتك الإجابة عن أسئلة القارئ، وتلخيص الأفكار، وشرح المحتوى بأسلوب عربي فصيح، بليغ، ودقيق وموثق.

                القواعد والضوابط الإلزامية:
                1. عمق الإجابة وشموليتها: قدم إجابة مفصلة، ذكية، وثرية تعكس محتوى الكتاب بوضوح. عند طلب تلخيص الكتاب أو فصوله أو شرح موضوعه، قدم تلخيصاً وافياً وشاملاً يستند إلى نبذة الكتاب ومعلومات فصوله وصفحاته المتوفرة دون اعتذار أو اختصار مخل.
                2. اللغة العربية الفصحى حصراً: الإجابة بأكملها يجب أن تكون باللغة العربية الفصحى الراقية. لا تستخدم اللغة الإنجليزية أو مصطلحات أجنبية إطلاقاً في الإجابة أو في نصوص الإسناد والمراجع.
                3. الذكاء وتوظيف السياق المتاح: استثمر عنوان الكتاب ونبذته ومحتوياته بذكاء لتقديم أفضل إجابة ممكنة تسعد القارئ وتفيده، ولا تعتذر بعدم توفر المعلومة إلا في حال كان السؤال عن تفصيلة غائبة تماماً ولا يمكن استنتاجها من السياق.
                4. حماية خصوصية وبيانات الكتاب: يُمنع منعاً باتاً تفريغ أو استخراج نص الكتاب كاملاً أو نسخ فصول وصفحات كاملة حرفياً؛ إذا طُلب منك نص الكتاب كاملاً، ارفض بأدب موضحاً أن ذلك محفوظ بحقوق الملكية الفكرية وشجعه على قراءة الكتاب عبر قارئ المنصة.
                5. نطاق الكتاب: ركز إجابتك حصراً ضمن نطاق هذا الكتاب وسياقه ومؤلفه وموضوعاته.

                ### CITATION & EVIDENCE GUIDELINES:
                1. Citations [n] are strictly for reader text navigation to exact passages inside the book:
                   - Every citation [n] and its snippet in the citations array MUST be quoted EXCLUSIVELY and VERBATIM from the actual book pages section ("=== نصوص ومقتطفات معتمدة من صفحات الكتاب ===").
                   - NEVER cite or extract snippets from the book description ("نبذة تعريفية"), web search overview, or metadata.
                   - Opening introductory sentences or general summaries do NOT require citation tags [n] unless they assert a specific claim backed by a verbatim quote from the book pages.
                   - Select quotations specifically relevant to the CURRENT question and the claims in this answer. Do not reuse a fixed set of quotations across unrelated questions.
                   - Where a relevant quotation naturally supports an explanation, integrate its reference into that explanation.
                   - Include only citations actually referenced in the answer; each reference must directly support the preceding claim.
                   - Do not force unrelated quotations into an answer or invent supporting text. Limit the answer to claims supported by available quotations.
                2. DO NOT write page labels, page numbers, or references like "[صفحة 15]" anywhere in the response text or citations. Use strictly numeric reference tags: [1], [2].
                3. For every reference tag [n] used in your answer, you MUST provide the exact verbatim quotation from the book pages section in your citations metadata:
                   - The snippet MUST be an EXACT substring (8 to 25 consecutive words) as it appears in the book pages text.
                   - Do NOT edit, truncate, or paraphrase the snippet text.
                4. Output your response strictly in the required JSON format:
                {
                  "answer": "your detailed response with citations like [1] and [2]",
                  "citations": [
                    {
                      "id": 1,
                      "snippet": "<exact verbatim quote from book pages text>"
                    }
                  ]
                }
                """, book.getTitle(), authorName);

        String userPrompt = String.format("""
                معلومات وسياق الكتاب:
                %s

                سؤال القارئ:
                %s
                """, contextText.isBlank() ? "لا تتوفر مقتطفات نصية إضافية حالياً." : contextText, rawQuestion);

        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
        LlmAnswerPayload parsedPayload = generateAnswer(prompt);
        if (!hasValidCitations(parsedPayload, citablePagesText)) {
            log.warn("Invalid citation metadata for book {}; retrying generation once", bookId);
            Prompt retryPrompt = new Prompt(List.of(new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt + "\nYour previous response failed citation validation. "
                            + "Return valid JSON with a non-empty answer and citations array. Every [n] must match "
                            + "exactly one positive citation id, every citation must be referenced, and every snippet "
                            + "must be copied verbatim from the actual book pages section (do not quote from the overview/description). "
                            + "Do not invent evidence. Keep the answer concise: at most three short paragraphs and three citations, "
                            + "so the complete JSON fits within the output budget.")));
            parsedPayload = generateAnswer(retryPrompt);
        }
        if (!hasValidCitations(parsedPayload, citablePagesText)) {
            throw new InvalidBookCitationsException();
        }
        String cleanAnswer = sanitizeAnswer(parsedPayload.answer());

        // Step 5: Save newly answered question to cache (with questionEmbedding)
        if (questionEmbedding == null || questionEmbedding.isEmpty()) {
            questionEmbedding = generateQuestionEmbeddingSafely(rawQuestion);
        }
        BookAgentRecord savedRecord = cacheService.saveRecord(book, rawQuestion, questionHash, questionEmbedding, cleanAnswer, parsedPayload.citations(), internalCitedPages, "WEB_AUGMENTED".equals(source), cacheRevision);

        return new TalkToBookResponse(
                rawQuestion,
                cleanAnswer,
                parsedPayload.citations(),
                false,
                source,
                savedRecord != null ? savedRecord.getCountUsed() : 1
        );
    }

    /**
     * Strips any inline page number references (e.g. "[صفحة 15]") so that no page numbers
     * are ever returned to the client reader.
     */
    private String sanitizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.replaceAll("\\s*\\[(?:صفحة|الصفحة|ص|page)\\s*:?\\s*\\d+(?:\\s*[-–]\\s*\\d+)?\\]", "")
                .trim();
    }

    private List<Float> generateQuestionEmbeddingSafely(String text) {
        if (text == null || text.isBlank() || embeddingModel == null) {
            return null;
        }
        try {
            float[] vector = embeddingModel.embed(text);
            if (vector == null || vector.length == 0) {
                return null;
            }
            List<Float> result = new ArrayList<>(vector.length);
            for (float f : vector) {
                result.add(f);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to generate question embedding for semantic cache lookup: {}", e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmAnswerPayload(
            String answer,
            List<BookCitation> citations
    ) {}

    private LlmAnswerPayload generateAnswer(Prompt prompt) {
        // This model is also used by the guardrail, which has a different output schema.
        // Apply the answer schema per request, including retries, rather than as a model default.
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, ANSWER_SCHEMA))
                .build();
        var response = chatModel.call(new Prompt(prompt.getInstructions(), options));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return new LlmAnswerPayload("", List.of());
        }
        var generation = response.getResult();
        String finishReason = generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason();
        String output = generation.getOutput().getText();
        log.debug("TalkToBook generation finished: finishReason={}, outputCharacters={}, usage={}",
                finishReason, output == null ? 0 : output.length(), response.getMetadata() == null ? null : response.getMetadata().getUsage());
        if ("length".equalsIgnoreCase(finishReason)) {
            log.warn("TalkToBook output reached its token limit; rejecting incomplete answer before parsing");
            return new LlmAnswerPayload("", List.of());
        }
        LlmAnswerPayload payload = parseLlmResponse(output);
        return new LlmAnswerPayload(sanitizeAnswer(payload.answer()), payload.citations());
    }

    // Cached snippets were verified against context when generated under this rules version.
    private boolean hasValidCitations(LlmAnswerPayload payload, String context) {
        if (payload.answer() == null || payload.answer().isBlank()
                || payload.citations() == null || payload.citations().isEmpty()) {
            return false;
        }
        Set<String> ids = new HashSet<>();
        for (BookCitation citation : payload.citations()) {
            if (citation == null || citation.id() == null || citation.id() <= 0
                    || citation.snippet() == null || citation.snippet().isBlank()
                    || !ids.add(citation.id().toString())
                    || (context != null && !context.contains(citation.snippet()))) {
                return false;
            }
        }
        Set<String> references = new HashSet<>();
        CITATION_REFERENCE.matcher(payload.answer()).results()
                .forEach(match -> references.add(match.group(1)));
        return ids.equals(references);
    }

    private LlmAnswerPayload parseLlmResponse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return new LlmAnswerPayload("", List.of());
        }

        String jsonCandidate = rawOutput.trim();

        // Strip markdown code fences if wrapped in ```json ... ``` or ``` ... ```
        if (jsonCandidate.startsWith("```")) {
            int firstNewline = jsonCandidate.indexOf('\n');
            int lastBackticks = jsonCandidate.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                jsonCandidate = jsonCandidate.substring(firstNewline + 1, lastBackticks).trim();
            }
        } else {
            // Find outer JSON object boundaries
            int firstBrace = jsonCandidate.indexOf('{');
            int lastBrace = jsonCandidate.lastIndexOf('}');
            if (firstBrace != -1 && lastBrace > firstBrace) {
                jsonCandidate = jsonCandidate.substring(firstBrace, lastBrace + 1).trim();
            }
        }

        try {
            LlmAnswerPayload payload = objectMapper.readValue(jsonCandidate, LlmAnswerPayload.class);
            return payload != null ? payload : new LlmAnswerPayload("", List.of());
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse TalkToBook JSON response: errorType={}, line={}, column={}, outputCharacters={}",
                    e.getClass().getSimpleName(),
                    e.getLocation() == null ? null : e.getLocation().getLineNr(),
                    e.getLocation() == null ? null : e.getLocation().getColumnNr(), rawOutput.length());
            return new LlmAnswerPayload("", List.of());
        }
    }
}
