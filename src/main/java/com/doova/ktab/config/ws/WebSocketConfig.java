package com.doova.ktab.config.ws;

import com.doova.ktab.features.tts.ws.ReaderTtsWebSocketHandler;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ReaderTtsWebSocketHandler readerTtsWebSocketHandler;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:4200,http://localhost:5173,https://ktab-rho.vercel.app}")
    private List<String> allowedOrigins;

    /**
     * No WebSocket compression: see {@link WebSocketExtensionsFilter}. Static so that registering a filter does not
     * force this configuration (and the handler it needs) to be created early.
     */
    @Bean
    public static FilterRegistrationBean<WebSocketExtensionsFilter> webSocketExtensionsFilter() {
        FilterRegistrationBean<WebSocketExtensionsFilter> registration = new FilterRegistrationBean<>(new WebSocketExtensionsFilter());
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = (allowedOrigins != null && !allowedOrigins.isEmpty())
                ? allowedOrigins.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .map(o -> o.replaceFirst("^(https?://[^/]+).*", "$1"))
                    .distinct()
                    .toArray(String[]::new)
                : new String[]{"http://localhost:3000", "https://ktab-rho.vercel.app"};

        registry.addHandler(readerTtsWebSocketHandler,
                        "/ws/reader/tts",
                        "/api/v1/ws/reader/tts",
                        "/Ktab-0.0.1-SNAPSHOT/ws/reader/tts",
                        "/Ktab-0.0.1-SNAPSHOT/api/v1/ws/reader/tts")
                .setAllowedOriginPatterns(origins);
    }
}
