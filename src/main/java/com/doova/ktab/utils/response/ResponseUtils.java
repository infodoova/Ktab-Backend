package com.doova.ktab.utils.response;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ResponseStatusException;

import java.io.PrintWriter;
import java.util.Optional;


public class ResponseUtils {

    // --- 1. Low-Level Servlet Response Utility (for Filters/Security) ---

    /**
     * Sends a response directly using HttpServletResponse, bypassing Spring's MVC handling.
     * Useful for custom security filters or error handling outside the controller layer.
     * @param object The response body object (will be serialized to JSON).
     * @param response The HttpServletResponse to write to.
     * @param httpStatus The HTTP status to set.
     */
    public static void send(Object object, HttpServletResponse response, HttpStatus httpStatus) {
        try {
            PrintWriter out = response.getWriter();
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(httpStatus.value());
            out.print(new ObjectMapper().writeValueAsString(object));
            out.flush();
        } catch (Exception e) {
            // Log the exception if needed, but suppress to avoid breaking filter chain
        }
    }

    // --- 2. Error Response Utilities ---

    /**
     * Throws a ResponseStatusException for immediate HTTP error responses within a controller.
     * @param message The error message.
     * @param status The HTTP status (e.g., HttpStatus.BAD_REQUEST).
     * @return ResponseStatusException (always thrown).
     */
    public static ResponseStatusException errorResponse(String message, HttpStatus status) {
        return new ResponseStatusException(status, message);
    }

    public static <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        ApiResponse<T> api = new ApiResponse<>();
        api.setSuccess(false);
        api.setMessage(message);
        api.setData(null);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(api);
    }

    // --- 3. Standard ResponseEntity Success Builders (Using ApiResponse) ---

    /**
     * Standard success response (200 OK) with data and a custom message.
     * @param object The response data payload.
     * @param message The custom success message.
     * @param <T> The type of the data object.
     * @return ResponseEntity with HttpStatus.OK.
     */
    public static <T> ResponseEntity<ApiResponse<T>> response(T object, String message) {
        ApiResponse<T> apiResponse = new ApiResponse<>(object, message);
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    /**
     * Standard success response (200 OK) with data and a default message.
     * @param object The response data payload.
     * @param <T> The type of the data object.
     * @return ResponseEntity with HttpStatus.OK.
     */
    public static <T> ResponseEntity<ApiResponse<T>> response(T object) {
        return response(object, "Request successful");
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        ApiResponse<T> api = new ApiResponse<>();
        api.setSuccess(false);
        api.setMessage(message);
        api.setData(null);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(api);
    }

    // --- 4. Specialized Success Response Builders ---

    /**
     * Handles Paged data response (200 OK). Page objects already contain pagination metadata.
     * @param page The Spring Data Page object.
     * @param <T> The type of content in the Page.
     * @return ResponseEntity with HttpStatus.OK containing the Page.
     */
    public static <T> ResponseEntity<Page<T>> response(Page<T> page) {
        return new ResponseEntity<>(page, HttpStatus.OK);
    }

    /**
     * Handles collection response, wrapping the Iterable content for consistency (200 OK).
     * @param iterable The collection data payload.
     * @param <T> The type of content in the Iterable.
     * @return ResponseEntity with HttpStatus.OK wrapping the iterable in a ContentWrapper.
     */
    public static <T> ResponseEntity<ContentWrapper<T>> response(Iterable<T> iterable) {
        return new ResponseEntity<>(new ContentWrapper<>(iterable), HttpStatus.OK);
    }

    // --- 5. Status-Specific Builders ---

    /**
     * Returns an empty 200 OK response.
     * @return ResponseEntity with HttpStatus.OK.
     */
    public static ResponseEntity<Void> ok() {
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Returns an empty 204 No Content response, typically for successful DELETE or PUT operations.
     * @return ResponseEntity with HttpStatus.NO_CONTENT.
     */
    public static ResponseEntity<Void> noContent() {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Returns a 201 Created response, typically for successful POST operations.
     * Includes the created object in the body with a success message.
     * @param object The newly created resource.
     * @param <T> The type of the created object.
     * @return ResponseEntity with HttpStatus.CREATED.
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(T object) {
        ApiResponse<T> apiResponse = new ApiResponse<>(object, "Resource created successfully");
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    /**
     * Returns a 404 Not Found response when an Optional is empty.
     * @param message The custom Not Found message.
     * @return ResponseStatusException (always thrown).
     */
    public static ResponseStatusException notFound(String message) {
        return errorResponse(message, HttpStatus.NOT_FOUND);
    }

    /**
     * Helper to retrieve value from Optional or throw a 404 exception.
     * @param optional The Optional containing the resource.
     * @param message The error message if the resource is not found.
     * @param <T> The type of the resource.
     * @return The resource if present.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T> T orNotFound(Optional<T> optional, String message) {
        return optional.orElseThrow(() -> notFound(message));
    }
}
