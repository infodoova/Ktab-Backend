package com.doova.ktab.features.talktobook.service.impl;

import com.doova.ktab.features.talktobook.config.TalkToBookProperties;
import com.doova.ktab.features.talktobook.dto.response.BookCitation;
import com.doova.ktab.features.talktobook.event.model.BookAgentRecordCreatedEvent;
import com.doova.ktab.features.talktobook.model.BookAgentRecord;
import com.doova.ktab.features.talktobook.repository.BookAgentRecordRepository;
import com.doova.ktab.features.talktobook.service.BookAgentRecordCacheService;
import com.doova.ktab.model.book.Book;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class BookAgentRecordCacheServiceImpl implements BookAgentRecordCacheService {

    // Bump alongside code changes to prompts, retrieval or citation rules.
    private static final String RESPONSE_RULES_VERSION = "5";

    private final BookAgentRecordRepository recordRepository;
    private final TalkToBookProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;

    public BookAgentRecordCacheServiceImpl(
            BookAgentRecordRepository recordRepository,
            TalkToBookProperties properties,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("talkToBookChatModel") OpenAiChatModel chatModel,
            ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Optional<BookAgentRecord> findSimilar(Long bookId, String questionHash, List<Float> questionEmbedding, String revision) {
        return findSimilar(bookId, questionHash, null, questionEmbedding, revision);
    }

    @Override
    @Transactional
    public Optional<BookAgentRecord> findSimilar(Long bookId, String questionHash, String rawQuestion, List<Float> questionEmbedding, String revision) {
        // Tier 1: Instant O(1) Exact Hash Lookup
        if (questionHash != null && !questionHash.isBlank()) {
            Optional<BookAgentRecord> exactMatch = recordRepository.findByBookIdAndQuestionHash(bookId, questionHash);
            if (exactMatch.isPresent() && isFresh(exactMatch.get(), revision)) {
                BookAgentRecord record = exactMatch.get();
                record.incrementCountUsed();
                recordRepository.save(record);
                log.info("TalkToBook Cache Hit [Exact Match] for book {}. Total usage: {}", bookId, record.getCountUsed());
                return Optional.of(record);
            }
        }

        // Fetch candidates for this book
        List<BookAgentRecord> candidates = recordRepository.findByBookId(bookId).stream()
                .filter(c -> isFresh(c, revision))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Tier 2: In-Memory Cosine Similarity across book records (<1ms for <= 500 records)
        if (questionEmbedding != null && !questionEmbedding.isEmpty()) {
            BookAgentRecord bestMatch = null;
            double highestSimilarity = 0.0;

            for (BookAgentRecord candidate : candidates) {
                if (candidate.getQuestionEmbedding() != null && !candidate.getQuestionEmbedding().isEmpty()) {
                    double sim = calculateCosineSimilarity(questionEmbedding, candidate.getQuestionEmbedding());
                    if (sim >= properties.getSimilarityThreshold() && sim > highestSimilarity) {
                        highestSimilarity = sim;
                        bestMatch = candidate;
                    }
                }
            }

            if (bestMatch != null) {
                bestMatch.incrementCountUsed();
                recordRepository.save(bestMatch);
                log.info("TalkToBook Cache Hit [Semantic Match: {:.4f}] for book {}. Total usage: {}",
                        highestSimilarity, bookId, bestMatch.getCountUsed());
                return Optional.of(bestMatch);
            }
        }

        // Tier 3: AI Semantic Intent Search (handles abbreviations like sum=summarize, typos, language variations)
        if (rawQuestion != null && !rawQuestion.isBlank() && chatModel != null) {
            BookAgentRecord aiMatch = findAiIntentMatch(rawQuestion, candidates);
            if (aiMatch != null) {
                aiMatch.incrementCountUsed();
                recordRepository.save(aiMatch);
                log.info("TalkToBook Cache Hit [AI Intent Match: '{}' -> '{}'] for book {}. Total usage: {}",
                        rawQuestion, aiMatch.getQuestion(), bookId, aiMatch.getCountUsed());
                return Optional.of(aiMatch);
            }
        }

        return Optional.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiMatchResponse(Integer matchIndex) {}

    private BookAgentRecord findAiIntentMatch(String rawQuestion, List<BookAgentRecord> candidates) {
        if (chatModel == null || rawQuestion == null || rawQuestion.isBlank() || candidates.isEmpty()) {
            return null;
        }

        // Limit candidate window to keep prompt small (<150 tokens) and response instant (<300ms)
        List<BookAgentRecord> topCandidates = candidates.stream().limit(15).toList();

        StringBuilder candidatesPrompt = new StringBuilder();
        for (int i = 0; i < topCandidates.size(); i++) {
            candidatesPrompt.append("[").append(i + 1).append("] \"")
                    .append(topCandidates.get(i).getQuestion().replace("\"", "\\\""))
                    .append("\"\n");
        }

        String prompt = String.format("""
                User question: "%s"
                Candidate cached questions:
                %s
                Which candidate question has the exact same intent or asks for the same content (accounting for abbreviations like sum=summarize, typos, or phrasing)?
                Respond ONLY with JSON: {"matchIndex": <1-based index or null>}
                """, rawQuestion.replace("\"", "\\\""), candidatesPrompt);

        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature(0.0)
                    .maxCompletionTokens(20)
                    .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                    .build();

            var response = chatModel.call(new Prompt(prompt, options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return null;
            }

            String content = response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                return null;
            }

            AiMatchResponse match = objectMapper.readValue(content.trim(), AiMatchResponse.class);
            if (match != null && match.matchIndex() != null && match.matchIndex() >= 1 && match.matchIndex() <= topCandidates.size()) {
                return topCandidates.get(match.matchIndex() - 1);
            }
        } catch (Exception e) {
            log.warn("AI intent cache matching failed gracefully: {}", e.getMessage());
        }

        return null;
    }

    @Override
    @Transactional
    public BookAgentRecord saveRecord(Book book, String question, String questionHash, List<Float> questionEmbedding,
                                      String answer, List<BookCitation> citations, List<Integer> citedPages, boolean isWebAugmented, String revision) {
        BookAgentRecord record = recordRepository.findByBookIdAndQuestionHash(book.getId(), questionHash)
                .orElseGet(BookAgentRecord::new);
        boolean isNew = record.getId() == null;
        record.setBook(book);
        record.setQuestion(question);
        record.setQuestionHash(questionHash);
        record.setQuestionEmbedding(questionEmbedding);
        record.setAnswer(answer);
        record.setCitations(citations != null ? citations : List.of());
        record.setCitedPages(citedPages != null ? citedPages : List.of());
        record.setCountUsed(isNew ? 1 : record.getCountUsed() + 1);
        record.setWebAugmented(isWebAugmented);
        record.setLastAccessedAt(Instant.now());
        record.setCacheRevision(revision);
        record.setAnswerGeneratedAt(Instant.now());

        BookAgentRecord saved = recordRepository.save(record);
        log.debug("Persisted new BookAgentRecord ID {} for book {}", saved.getId(), book.getId());

        // Publish event for out-of-band 500-record threshold maintenance
        if (isNew) {
            eventPublisher.publishEvent(new BookAgentRecordCreatedEvent(book.getId()));
        }

        return saved;
    }

    private boolean isFresh(BookAgentRecord record, String revision) {
        // Cache validity depends on content and response rules, never elapsed time.
        return revision != null && revision.equals(record.getCacheRevision())
                && record.getAnswer() != null && !record.getAnswer().isBlank();
    }

    @Override
    @Transactional(readOnly = true)
    public String computeRevision(Book book) {
        String author = book.getCustomAuthorName() != null ? book.getCustomAuthorName()
                : book.getAuthor() == null ? null
                : book.getAuthor().getFirstName() + " " + book.getAuthor().getLastName();
        String contentRevision = recordRepository.computeContentRevision(book.getId());
        if (contentRevision == null) {
            return null; // Fail closed if a content snapshot cannot be obtained.
        }
        // Length prefixes preserve punctuation, whitespace and field boundaries exactly.
        StringBuilder snapshot = new StringBuilder();
        for (Object value : new Object[]{RESPONSE_RULES_VERSION, book.getTitle(), book.getDescription(), author,
                contentRevision, properties.getAnswerVersion(), properties.getModel(),
                properties.getTemperature(), properties.getMaxOutputTokens(), properties.getMaxInputTokens(),
                properties.isWebSearchEnabled(), properties.getSearchBaseUrl()}) {
            String text = value == null ? "" : value.toString();
            snapshot.append(value == null ? -1 : text.length()).append(':').append(text);
        }
        return sha256(snapshot.toString());
    }

    @Override
    public String computeHash(String question) {
        if (question == null) {
            return "";
        }
        // Normalize: lowercase, trim, strip punctuation and superfluous whitespace
        String normalized = question.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return sha256(normalized);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing from runtime", e);
        }
    }

    private double calculateCosineSimilarity(List<Float> vectorA, List<Float> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            float a = vectorA.get(i);
            float b = vectorB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
