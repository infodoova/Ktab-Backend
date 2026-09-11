package com.doova.ktab.controller.v1.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FaviconControllerTest {

    @Test
    @DisplayName("Should return 204 No Content for favicon requests")
    void returnNoFavicon_returnsNoContent() {
        FaviconController controller = new FaviconController();
        ResponseEntity<Void> response = controller.returnNoFavicon();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }
}
