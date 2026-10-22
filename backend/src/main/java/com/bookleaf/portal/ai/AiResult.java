package com.bookleaf.portal.ai;

/**
 * Normalised result of an LLM call.
 *
 * @param cached true when served from the local cache — no billable call was made
 */
public record AiResult(String text,
                       int promptTokens,
                       int completionTokens,
                       String model,
                       String provider,
                       boolean cached) {

    public static AiResult cachedResult(String text, String model, String provider) {
        return new AiResult(text, 0, 0, model, provider, true);
    }
}
