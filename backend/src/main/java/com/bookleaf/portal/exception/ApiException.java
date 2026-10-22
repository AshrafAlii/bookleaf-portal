package com.bookleaf.portal.exception;

import java.util.Map;
import java.util.UUID;

/**
 * Domain exception carrying an HTTP status and a human-readable message.
 * Handled centrally by {@link GlobalExceptionHandler} so every error response
 * has the same shape.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String error;
    private final Map<String, String> fieldErrors;

    public ApiException(int status, String error, String message) {
        this(status, error, message, Map.of());
    }

    public ApiException(int status, String error, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.error = error;
        this.fieldErrors = fieldErrors;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "Not Found", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, "Forbidden", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "Bad Request", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "Conflict", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "Unauthorized", message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(422, "Unprocessable Entity", message);
    }

    /** 503 — used when an upstream dependency (e.g. the LLM provider) is unavailable. */
    public static ApiException serviceUnavailable(String message) {
        return new ApiException(503, "Service Unavailable", message);
    }

    /** Correlation id echoed back to the client so support can trace a request. */
    public String correlationId() {
        return UUID.randomUUID().toString();
    }
}
