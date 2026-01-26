package com.doova.ktab.config.elevenlabs;

import com.doova.ktab.dto.tts.ElevenLabsChunkResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class ElevenLabsClient {

    private final ElevenLabsProperties props;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private static final int MAX_MSG = 2 * 1024 * 1024;

    // Server + client timeout harmony
    private static final int INACTIVITY_TIMEOUT_SEC = 180;
    private static final Duration CLIENT_IDLE_TIMEOUT = Duration.ofSeconds(190);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);

    public ElevenLabsClient(
            ElevenLabsProperties props,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Stream TTS audio from ElevenLabs using WebSocket.
     */
    public Flux<ElevenLabsChunkResponse> streamTts(
            String text,
            String voiceId,
            Long seed
    ) {

        String vid = (voiceId == null || voiceId.isBlank())
                ? props.getVoiceId()
                : voiceId;

        String url = String.format(
                "wss://api.elevenlabs.io/v1/text-to-speech/%s/stream-input" +
                        "?model_id=%s" +
                        "&sync_alignment=true" +
                        "&auto_mode=true" +
                        "&inactivity_timeout=%d" +
                        (seed != null ? "&seed=%d" : ""),
                vid,
                props.getModelId(),
                INACTIVITY_TIMEOUT_SEC,
                seed
        );

        // Backpressure-safe sink (bounded)
        Sinks.Many<ElevenLabsChunkResponse> sink =
                Sinks.many().unicast().onBackpressureBuffer();

        AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(MAX_MSG);
            container.setDefaultMaxBinaryMessageBufferSize(MAX_MSG);

            StandardWebSocketClient client = new StandardWebSocketClient(container);


            CompletableFuture<WebSocketSession> future = client.execute(
                    new ElevenLabsHandler(
                            sink,
                            sessionRef,
                            text,
                            sample
                    ),
                    String.valueOf(URI.create(url))
            );

            future.orTimeout(CONNECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((session, error) -> {
                        if (error != null) {
                            meterRegistry.counter("tts.elevenlabs.connection.errors").increment();
                            sink.tryEmitError(new ElevenLabsConnectionException("Connection failed", error));
                        } else {
                            sessionRef.set(session);
                            meterRegistry.counter("tts.elevenlabs.connections.opened").increment();
                        }
                    });

        } catch (Exception e) {
            sink.tryEmitError(e);
        }

        return sink.asFlux()
                .timeout(CLIENT_IDLE_TIMEOUT)
                .doFinally(signal -> {
                    WebSocketSession session = sessionRef.get();
                    if (session != null && session.isOpen()) {
                        try {
                            session.close(CloseStatus.NORMAL);
                        } catch (Exception ignored) {}
                    }
                    meterRegistry.counter("tts.elevenlabs.connections.closed").increment();
                });
    }

    /**
     * WebSocket handler.
     */
    private class ElevenLabsHandler extends TextWebSocketHandler {

        private final Sinks.Many<ElevenLabsChunkResponse> sink;
        private final AtomicReference<WebSocketSession> sessionRef;
        private final String text;
        private final Timer.Sample sample;

        private int chunks = 0;

        ElevenLabsHandler(
                Sinks.Many<ElevenLabsChunkResponse> sink,
                AtomicReference<WebSocketSession> sessionRef,
                String text,
                Timer.Sample sample
        ) {
            this.sink = sink;
            this.sessionRef = sessionRef;
            this.text = text;
            this.sample = sample;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            sessionRef.set(session);

            // Init (no API key here – already in headers)
            send(session, Map.of(
                    "voice_settings", Map.of(
                            "stability", 0.65,
                            "similarity_boost", 0.6
                    ),
                    "xi_api_key",props.getApiKey()
            ));

            // Send text
            send(session, Map.of(
                    "text", text,
                    "try_trigger_generation", true
            ));

            // End of input
            send(session, Map.of("text", ""));

            meterRegistry.counter("tts.elevenlabs.requests.sent").increment();
            meterRegistry.summary("tts.elevenlabs.text.length").record(text.length());
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage msg) {
            try {
                ElevenLabsChunkResponse chunk =
                        objectMapper.readValue(msg.getPayload(), ElevenLabsChunkResponse.class);

                chunks++;
                sink.tryEmitNext(chunk);

//                // Let server close naturally after final output
//                if (Boolean.TRUE.equals(chunk.isFinal())) {
//                    log.debug("Final chunk received");
//                }

            } catch (Exception e) {
                sink.tryEmitError(e);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            sample.stop(meterRegistry.timer("tts.elevenlabs.stream.duration"));
            meterRegistry.summary("tts.elevenlabs.chunks.received").record(chunks);

            if (status.getCode() == CloseStatus.NORMAL.getCode()) {
                sink.tryEmitComplete();
            } else {
                sink.tryEmitError(
                        new ElevenLabsConnectionException(
                                "Abnormal close: " + status.getCode() + " " + status.getReason()
                        )
                );
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable ex) {
            sink.tryEmitError(ex);
        }

        private void send(WebSocketSession session, Map<String, Object> payload) throws Exception {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        }
    }

    // === Exceptions ===

    public static class ElevenLabsConnectionException extends RuntimeException {
        public ElevenLabsConnectionException(String msg, Throwable cause) {
            super(msg, cause);
        }
        public ElevenLabsConnectionException(String msg) {
            super(msg);
        }
    }
}
