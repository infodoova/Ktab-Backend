package com.doova.doovafeeds.utils;

import com.doova.doovafeeds.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.PrintWriter;

public class ResponseUtils {

    public static void send(Object object, HttpServletResponse response, HttpStatus httpStatus) {
        try {
            PrintWriter out = response.getWriter();
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(httpStatus.value());
            out.print(new ObjectMapper().writeValueAsString(object));
            out.flush();
        } catch (Exception e) {
        }
    }

    public static ResponseStatusException errorResponse(String message, HttpStatus status) {
        return new ResponseStatusException(status, message);
    }

    public static ResponseEntity response(String message, HttpStatus status) {
        return new ResponseEntity(new MessageWrapper(message), status);
    }

    public static ResponseEntity response(Page page) {
        return new ResponseEntity(page, HttpStatus.OK);
    }

    public static ResponseEntity response(Iterable iterable) {
        return new ResponseEntity(new ContentWrapper(iterable), HttpStatus.OK);
    }

        public static ResponseEntity response(Object object) {
            return response(object, "Request successful");
    }

    public static ResponseEntity<ApiResponse> response(Object object, String message) {
        ApiResponse apiResponse = new ApiResponse(object, message);
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    public static ResponseEntity empty() {
        return new ResponseEntity(HttpStatus.OK);
    }

}
