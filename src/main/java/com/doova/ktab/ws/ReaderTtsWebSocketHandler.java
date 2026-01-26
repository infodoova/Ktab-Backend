package com.doova.ktab.ws;

import com.doova.ktab.dto.response.TextRangeResponse;
import com.doova.ktab.dto.*;
import com.doova.ktab.dto.tts.TextChunk;
import com.doova.ktab.dto.tts.TtsStreamChunk;
import com.doova.ktab.dto.tts.TtsWsChunkMessage;
import com.doova.ktab.dto.tts.WordTiming;
import com.doova.ktab.exception.ElevenLabsQuotaExceededException;
import com.doova.ktab.service.elevenlabs.ElevenLabsTimestampTtsService;
import com.doova.ktab.service.interfaces.book.BookTextService;
import com.doova.ktab.utils.AudioDecoder;
import com.doova.ktab.utils.TtsAlignmentMapper;
import com.doova.ktab.ws.dto.TtsStreamParams;
import com.doova.ktab.ws.utils.ArabicTtsChunker;
import com.doova.ktab.ws.utils.ElevenLabsV3TextChunker;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@NonNullApi
@RequiredArgsConstructor
public class ReaderTtsWebSocketHandler extends TextWebSocketHandler {

    private final ElevenLabsTimestampTtsService elevenLabsTts;
    private final BookTextService bookTextService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /* ============================================================
       STATE
       ============================================================ */

    private final Map<String, Disposable> activeStreams = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> seqCounters = new ConcurrentHashMap<>();
    private final Map<String, com.google.common.util.concurrent.AtomicDouble> durationCounters = new ConcurrentHashMap<>();
    private final Map<String, Object> sendLocks = new ConcurrentHashMap<>();
    private final Map<String, Deque<TtsStreamParams>> streamQueues = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> isStreaming = new ConcurrentHashMap<>();

    private final Map<String, Deque<String>> previousRequestIds = new ConcurrentHashMap<>();

    private static final String SAFE_SESSION_ATTR = "SAFE_SESSION";
    private static final int MAX_PREV_REQUEST_IDS = 3;
    private static final int MAX_CONTEXT_WORDS = 50;

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        withSessionMdc(session, () -> {
            var safe = new ConcurrentWebSocketSessionDecorator(session, 30_000, 50 * 1024 * 1024);
            session.getAttributes().put(SAFE_SESSION_ATTR, safe);
            seqCounters.put(session.getId(), new AtomicLong(0));
            durationCounters.put(session.getId(), new com.google.common.util.concurrent.AtomicDouble(0.0));

            previousRequestIds.put(session.getId(), new ArrayDeque<>(MAX_PREV_REQUEST_IDS));

            meterRegistry.counter("tts.websocket.connections.opened").increment();
            log.info("WS_CONNECTED {}", session.getId());

            sendJson(session, Map.of("type", "connected", "audioFormat", Map.of("codec", "mp3", "sampleRate", 44100)));
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        withSessionMdc(session, () -> {
            try {
                @SuppressWarnings("unchecked") Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);

                String action = (String) payload.getOrDefault("action", "stream");

                switch (action) {
                    case "stream" -> enqueueStream(session, payload);
                    case "stop" -> stop(session);
                    case "ping" -> sendJson(session, Map.of("type", "pong"));
                    default -> sendError(session, "UNKNOWN_ACTION", action);
                }
            } catch (Exception e) {
                log.error("WS_PARSE_ERROR", e);
                sendError(session, "PARSE_ERROR", "Invalid payload");
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        withSessionMdc(session, () -> {
            stop(session);
            String sid = session.getId();

            activeStreams.remove(sid);
            seqCounters.remove(sid);
            durationCounters.remove(sid);
            sendLocks.remove(sid);
            streamQueues.remove(sid);
            isStreaming.remove(sid);
            previousRequestIds.remove(sid);

            meterRegistry.counter("tts.websocket.connections.closed").increment();
            log.info("WS_CLOSED {}", sid);
        });
    }

    /* ============================================================
       STREAM QUEUE
       ============================================================ */

    private void enqueueStream(WebSocketSession session, Map<String, Object> payload) {
        String sid = session.getId();
        TtsStreamParams params = TtsStreamParams.fromMap(payload);

        log.info(">>> WS_ENQUEUE sid={} bookId={} range={}-{}", sid, params.bookId(), params.start(), params.end());

        streamQueues.computeIfAbsent(sid, k -> new ArrayDeque<>()).addLast(params);
        AtomicBoolean flag = isStreaming.computeIfAbsent(sid, k -> new AtomicBoolean(false));

        if (flag.compareAndSet(false, true)) {
            startNextStream(session);
        }
    }

    private void startNextStream(WebSocketSession session) {
        String sid = session.getId();
        Deque<TtsStreamParams> queue = streamQueues.get(sid);

        if (queue == null || queue.isEmpty()) {
            log.info("--- WS_QUEUE_EMPTY sid={}", sid);
            Optional.ofNullable(isStreaming.get(sid)).ifPresent(f -> f.set(false));
            return;
        }

        TtsStreamParams params = queue.pollFirst();
        log.info(">>> WS_START_STREAM sid={} bookId={} range={}-{} queueRemaining={}", sid, params.bookId(), params.start(), params.end(), queue.size());
        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            /* -----------------------------
               1) FETCH EXTENDED CONTEXT
               ----------------------------- */

//            int safeStart = Math.max(0, params.start() - MAX_CONTEXT_WORDS);
//            int safeEnd = params.end() + MAX_CONTEXT_WORDS;
//
//            TextRangeResponse extended = bookTextService.getTextByWordRange(params.bookId(), safeStart, safeEnd);
//
//            List<String> words = splitWordsPreserveArabic(extended.text());
//
//            int localStart = params.start() - safeStart;
//            int localEnd = params.end() - safeStart;
//
//            /* -----------------------------
//               2) CONTEXT (NOT SPOKEN)
//               ----------------------------- */
//
//            String prevContext = String.join(" ", words.subList(Math.max(0, localStart - MAX_CONTEXT_WORDS), localStart));
//
//            String nextContext = String.join(" ", words.subList(localEnd, Math.min(words.size(), localEnd + MAX_CONTEXT_WORDS)));
//
//            /* -----------------------------
//               3) MAIN TEXT ONLY (SPOKEN)
//               ----------------------------- */
//
//            String mainText = String.join(" ", words.subList(localStart, localEnd));
//
//            int mainStartChar = extended.startChar() + charOffsetUntilWord(words, localStart);
//
//            List<TextChunk> chunks = ArabicTtsChunker.chunk(mainText, mainStartChar);


            TextRangeResponse mainText = bookTextService.getTextByWordRange(params.bookId(), params.start(), params.end());
            log.info("--- WS_TEXT_FETCHED sid={} textLen={}", sid, mainText.text().length());

            /* -----------------------------
               3) MAIN TEXT ONLY (SPOKEN)
               ----------------------------- */

            //   List<TextChunk> chunks = ArabicTtsChunker.chunk(mainText.text(), mainText.startChar());

            List<TextChunk> chunksForV3 = ElevenLabsV3TextChunker.chunk(mainText.text(), mainText.startChar());

            log.info("WS_TTS_RANGE sid={} bookId={} start={} end={} chunksCount={}", sid, params.bookId(), params.start(), params.end(), chunksForV3.size());

            /* -----------------------------
               4) STREAM CHUNKS
               ----------------------------- */

            if (chunksForV3.isEmpty()) {
                log.warn("WS_TTS_EMPTY_TEXT bookId={} start={} end={}", params.bookId(), params.start(), params.end());
                timer.stop(meterRegistry.timer("tts.websocket.request.duration", "status", "empty"));
                sendJson(session, Map.of("type", "complete"));
                startNextStream(session);
                return;
            }

            Mono<Void> pipeline = Flux.fromIterable(chunksForV3).index().concatMap(tuple -> processChunk(session, params.voiceId(), chunksForV3, tuple, "", "")).then();

            Disposable d = pipeline.doFinally(sig -> {
                log.info("<<< WS_STREAM_FINISH sid={} status={}", sid, sig.name());
                timer.stop(meterRegistry.timer("tts.websocket.request.duration", "status", sig.name()));
                sendJson(session, Map.of("type", "complete"));
                startNextStream(session);
            }).subscribe();

            activeStreams.put(sid, d);

        } catch (Exception e) {
            handleStreamError(session, e, timer);
        }
    }

    /* ============================================================
       CHUNK PROCESSING
       ============================================================ */

    private Mono<Void> processChunk(WebSocketSession session, String voiceId, List<TextChunk> chunks, reactor.util.function.Tuple2<Long, TextChunk> tuple, String prevContext, String nextContext) {
        int idx = tuple.getT1().intValue();
        TextChunk chunk = tuple.getT2();
        boolean isLastChunk = (idx == chunks.size() - 1);

        String sid = session.getId();
        List<String> prevIds = snapshotPreviousRequestIds(sid);
        String prev = (idx == 0) ? prevContext : chunks.get(idx - 1).text();
        String next = isLastChunk ? nextContext : chunks.get(idx + 1).text();

        AtomicBoolean stored = new AtomicBoolean(false);

        log.info("WS_TTS_CHUNK sid={} idx={}/{} textLen={} startChar={} prevIds={}", sid, idx, chunks.size(), chunk.text().length(), chunk.startChar(), prevIds);

        return elevenLabsTts.streamWithTimestamps(chunk.text(), voiceId, prev, next, prevIds, reqId -> {
                    if (stored.compareAndSet(false, true)) {
                        log.info("--- WS_CAPTURED_ID sid={} reqId={}", sid, reqId);
                        rememberRequestId(sid, reqId);
                    }
                })

                // 👇 handle audio + alignment
                .doOnNext(resp -> {
                        handleTtsResponse(session, chunk, idx, resp);
                })

                // 👇 convert Mono<TtsStreamChunk> → Mono<Void>
                .then()

                .onErrorResume(e -> {
                    log.error("❌ Chunk TTS failed idx={} err={}", idx, e.toString());
                    return Mono.empty();
                });
    }



    /* ============================================================
       OUTPUT
       ============================================================ */

    private void handleTtsResponse(WebSocketSession session, TextChunk chunk, int index, TtsStreamChunk resp) {
        long seq = seqCounters.get(session.getId()).incrementAndGet();
        var durationRef = durationCounters.get(session.getId());
        double currentOffset = durationRef != null ? durationRef.get() : 0.0;

        log.info("<<< WS_HANDLE_RESP sid={} idx={} hasAudio={} hasAlign={} offset={}", session.getId(), index, (resp.audioBase64()!=null), resp.hasAlignment(), currentOffset);

        if (resp.hasAlignment()) {
            // ElevenLabs NDJSON typically provides timing in normalized_alignment
            var alignment = (resp.normalizedAlignment() != null) ? resp.normalizedAlignment() : resp.alignment();
            var timings = TtsAlignmentMapper.mapToWordTimings(alignment, chunk.startChar());

            // Adjust timings by the cumulative duration offset
            List<WordTiming> adjustedTimings = new ArrayList<>();
            double maxEndTime = currentOffset;

            for (WordTiming t : timings) {
                double newStart = t.startSeconds() + currentOffset;
                double newEnd = t.endSeconds() + currentOffset;
                if (newEnd > maxEndTime) {
                    maxEndTime = newEnd;
                }
                adjustedTimings.add(new WordTiming(
                        t.word(),
                        newStart,
                        newEnd,
                        t.durationSeconds(),
                        t.startChar(),
                        t.endChar()
                ));
            }
            
            // Update the duration counter for the NEXT chunk
            if (durationRef != null) {
                 durationRef.set(maxEndTime); 
            }

            sendJson(session, new TtsWsChunkMessage("alignment", index, seq, adjustedTimings));
        }

        if (resp.audioBase64() != null) {
            byte[] audio = AudioDecoder.decode(resp.audioBase64());
            sendBinary(session, audio, seq);
        }
    }

    private void sendBinary(WebSocketSession session, byte[] audio, long seq) {
        log.info("<<< WS_SEND_BINARY sid={} seq={} bytes={}", session.getId(), seq, audio.length);
        executeLocked(session, () -> {
            ByteBuffer buf = ByteBuffer.allocate(8 + audio.length).order(ByteOrder.BIG_ENDIAN).putLong(seq).put(audio).flip();

            sendMessage(session, new BinaryMessage(buf));
        });
    }

    private void sendJson(WebSocketSession session, Object payload) {
        if (!(payload instanceof Map && "pong".equals(((Map<?, ?>) payload).get("type")))) {
             log.info("<<< WS_SEND_JSON sid={} payload={}", session.getId(), payload);
        }
        executeLocked(session, () -> {
            sendMessage(session, new TextMessage(objectMapper.writeValueAsString(payload)));
        });
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private void sendMessage(WebSocketSession session, WebSocketMessage<?> msg) throws Exception {
        WebSocketSession safe = (WebSocketSession) session.getAttributes().get(SAFE_SESSION_ATTR);

        if (safe != null && safe.isOpen()) {
            safe.sendMessage(msg);
        }
    }

    private void executeLocked(WebSocketSession session, ThrowingRunnable r) {
        synchronized (sendLocks.computeIfAbsent(session.getId(), k -> new Object())) {
            try {
                r.run();
            } catch (Exception e) {
                log.error("WS_SEND_FAILED", e);
            }
        }
    }

    private void stop(WebSocketSession session) {
        String sid = session.getId();

        Optional.ofNullable(activeStreams.remove(sid)).ifPresent(Disposable::dispose);
        Optional.ofNullable(streamQueues.get(sid)).ifPresent(Deque::clear);
        Optional.ofNullable(isStreaming.get(sid)).ifPresent(f -> f.set(false));

        resetPreviousRequestIds(sid);
    }

    private void handleStreamError(WebSocketSession session, Throwable err, Timer.Sample timer) {
        timer.stop(meterRegistry.timer("tts.websocket.request.duration", "status", "error"));

        if (err instanceof ElevenLabsQuotaExceededException) {
            sendJson(session, Map.of("type", "error", "code", "ELEVENLABS_CREDITS_EXHAUSTED"));
            stop(session);
        } else {
            sendError(session, "STREAM_ERROR", err.getMessage());
            startNextStream(session);
        }
    }

    private void sendError(WebSocketSession session, String code, String msg) {
        sendJson(session, Map.of("type", "error", "code", code, "message", msg));
    }

    private void withSessionMdc(WebSocketSession session, Runnable r) {
        try (var ignored = MDC.putCloseable("wsSessionId", session.getId())) {
            r.run();
        }
    }

    /* ============================================================
       TEXT UTILITIES (KEEP YOUR OWN IF EXISTS)
       ============================================================ */

    private List<String> splitWordsPreserveArabic(String text) {
        return Arrays.asList(text.trim().split("\\s+"));
    }

    private int charOffsetUntilWord(List<String> words, int wordIndex) {
        int offset = 0;
        for (int i = 0; i < wordIndex; i++) {
            offset += words.get(i).length() + 1;
        }
        return offset;
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

       /* ============================================================
       CONTINUITY (previous_request_ids) HELPERS
       ============================================================ */

    private void resetPreviousRequestIds(String sid) {
        previousRequestIds.put(sid, new ArrayDeque<>(MAX_PREV_REQUEST_IDS));
    }

    private List<String> snapshotPreviousRequestIds(String sid) {
        Deque<String> deque = previousRequestIds.get(sid);
        if (deque == null || deque.isEmpty()) return List.of();
        return List.copyOf(deque); // oldest -> newest
    }

    private void rememberRequestId(String sid, String requestId) {
        Deque<String> deque = previousRequestIds.computeIfAbsent(sid, k -> new ArrayDeque<>(MAX_PREV_REQUEST_IDS));

        // Optional: avoid duplicates
        if (!deque.isEmpty() && requestId.equals(deque.peekLast())) {
            return;
        }

        while (deque.size() >= MAX_PREV_REQUEST_IDS) {
            deque.removeFirst(); // drop oldest
        }
        deque.addLast(requestId);
    }
}
