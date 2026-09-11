package com.doova.ktab.utils.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;

/**
 * Utility for securely resolving client IP addresses from HTTP requests.
 * Accounts for reverse proxies, load balancers, and standard loopback representations.
 */
public final class ClientIpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String IPV6_LOCAL = "0:0:0:0:0:0:0:1";
    private static final String IPV4_LOCAL = "127.0.0.1";

    private static final String[] CANDIDATE_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private ClientIpUtils() {
    }

    /**
     * Extracts the client IP from request headers or remote address.
     *
     * @param request the incoming HTTP request
     * @return resolved client IP address string
     */
    public static String getClientIp(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        for (String header : CANDIDATE_HEADERS) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                // X-Forwarded-For can be a comma-separated list of client and proxies: client, proxy1, proxy2
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return normalizeIp(ip);
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return normalizeIp(remoteAddr);
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return IPV4_LOCAL;
        }
        String trimmed = ip.trim();
        if (IPV6_LOCAL.equals(trimmed)) {
            return IPV4_LOCAL;
        }
        return trimmed;
    }
}
