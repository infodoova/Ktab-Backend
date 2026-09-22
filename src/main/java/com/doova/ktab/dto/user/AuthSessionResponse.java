package com.doova.ktab.dto.user;

/**
 * Returned in the JSON body of auth endpoints (login, google, refresh-token).
 * Tokens are delivered exclusively via HttpOnly cookies; this DTO carries only
 * the metadata the frontend needs to schedule a silent token refresh.
 */
public record AuthSessionResponse(Long expiresIn) {}
