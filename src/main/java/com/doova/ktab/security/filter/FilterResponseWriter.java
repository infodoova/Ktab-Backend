package com.doova.ktab.security.filter;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.utils.response.ResponseUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class FilterResponseWriter {

    private final MessageSource messageSource;

    public void writeError(
            HttpServletResponse response,
            HttpStatus status,
            ApiMessageKey messageKey
    ) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        String localizedMessage = messageKey.getMessage(messageSource);
        ApiResponse<Void> body = ApiResponse.error(localizedMessage, status);

        ResponseUtils.send(body, response, status);
    }
}
