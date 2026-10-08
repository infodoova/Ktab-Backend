package com.doova.ktab.features.tts.ws;

import com.doova.ktab.features.extraction.BookExtractionQueryService;
import com.doova.ktab.features.tts.service.ElevenLabsTimestampTtsService;
import com.doova.ktab.service.book.BookTextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReaderTtsWebSocketHandlerTest {

    @Test
    void synthesisFailureIsReportedInsteadOfCompletingWithNoAudio() throws Exception {
        var tts = mock(ElevenLabsTimestampTtsService.class);
        var session = mock(WebSocketSession.class);
        var messages = new ArrayList<String>();
        when(session.getId()).thenReturn("test-session");
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            WebSocketMessage<?> message = invocation.getArgument(0);
            if (message instanceof TextMessage textMessage) messages.add(textMessage.getPayload());
            return null;
        }).when(session).sendMessage(any(WebSocketMessage.class));
        when(tts.streamWithTimestamps(anyString(), anyString(), anyString(), anyString(), anyList(), any()))
                .thenReturn(Mono.error(new IllegalStateException("upstream unavailable")));

        var handler = new ReaderTtsWebSocketHandler(tts, mock(BookTextService.class),
                mock(BookExtractionQueryService.class), new ObjectMapper(), new SimpleMeterRegistry());
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
                "action", "stream", "bookId", 123, "voiceId", "voice", "text", "Hello reader"))));

        assertThat(messages).anyMatch(message -> message.contains("\"code\":\"SYNTHESIS_FAILED\""));
        assertThat(messages).noneMatch(message -> message.contains("\"type\":\"complete\""));
    }
}
