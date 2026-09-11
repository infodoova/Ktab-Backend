package com.doova.ktab.config.ws;

import com.doova.ktab.features.tts.ws.ReaderTtsWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ReaderTtsWebSocketHandler readerTtsWebSocketHandler;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:4200,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = (allowedOrigins != null && !allowedOrigins.isEmpty())
                ? allowedOrigins.toArray(new String[0])
                : new String[]{"http://localhost:3000"};

        registry.addHandler(readerTtsWebSocketHandler, "/ws/reader/tts")
                .setAllowedOrigins(origins);
    }
}
