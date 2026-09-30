package com.doova.ktab.controller.v1.internal;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles browser favicon requests with 204 No Content to prevent NoResourceFoundException.
 */
@Hidden
@RestController
public class FaviconController {

    @GetMapping("favicon.ico")
    public ResponseEntity<Void> returnNoFavicon() {
        return ResponseEntity.noContent().build();
    }
}
