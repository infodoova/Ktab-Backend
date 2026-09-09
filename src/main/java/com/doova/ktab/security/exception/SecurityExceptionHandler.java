package com.doova.ktab.security.exception;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.MessageStatus;
import com.doova.ktab.utils.response.ResponseUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final MessageSource messageSource;

    // =========================================================================
    // UNAUTHORIZED (401)
    // =========================================================================
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {

        ApiResponse<Void> body = ApiResponse.error(ApiMessageKey.SECURITY_UNAUTHORIZED.getMessage(messageSource), HttpStatus.UNAUTHORIZED);

        ResponseUtils.send(body, response, HttpStatus.UNAUTHORIZED);
    }

    // =========================================================================
    // FORBIDDEN (403)
    // =========================================================================
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {

        ApiResponse<Void> body = ApiResponse.error(ApiMessageKey.SECURITY_ACCESS_DENIED.getMessage(messageSource), HttpStatus.FORBIDDEN);

        ResponseUtils.send(body, response, HttpStatus.FORBIDDEN);
    }
}
