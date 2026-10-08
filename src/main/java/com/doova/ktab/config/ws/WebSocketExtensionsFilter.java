package com.doova.ktab.config.ws;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Keeps WebSocket compression ({@code permessage-deflate}) from being negotiated, by hiding the browser's
 * {@code Sec-WebSocket-Extensions} request header from Tomcat on WebSocket upgrades. With nothing requested, nothing is agreed.
 * <p>
 * The reader's audio is MP3, which is already compressed, so deflating it saves nothing. The compression is also something
 * the browser and Tomcat must agree on frame by frame, with a window shared from one message to the next, and iPhone's
 * WebSocket stack drops the connection (code 1006) on the first message after a binary one. Without compression every
 * browser just reads plain frames.
 * <p>
 * Tomcat offers no switch for this: it falls back to its built-in extension whenever an endpoint lists none.
 */
public class WebSocketExtensionsFilter extends OncePerRequestFilter {

    static final String EXTENSIONS_HEADER = "Sec-WebSocket-Extensions";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isWebSocketUpgrade(request)) {
            chain.doFilter(new WithoutExtensionsHeader(request), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static boolean isWebSocketUpgrade(HttpServletRequest request) {
        String upgrade = request.getHeader("Upgrade");
        return upgrade != null && upgrade.equalsIgnoreCase("websocket");
    }

    /** The same request, as if the browser had not asked for any extension. */
    private static final class WithoutExtensionsHeader extends HttpServletRequestWrapper {

        WithoutExtensionsHeader(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return isExtensions(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isExtensions(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                    .filter(header -> !isExtensions(header))
                    .toList());
        }

        private static boolean isExtensions(String name) {
            return name != null && name.toLowerCase(Locale.ROOT).equals(EXTENSIONS_HEADER.toLowerCase(Locale.ROOT));
        }
    }
}
