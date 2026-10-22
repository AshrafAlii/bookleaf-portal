package com.bookleaf.portal.ai;

/**
 * Thrown when the LLM provider is unreachable, unauthenticated, rate limited or
 * returns an unusable response. Callers degrade gracefully instead of failing
 * the user's request.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
