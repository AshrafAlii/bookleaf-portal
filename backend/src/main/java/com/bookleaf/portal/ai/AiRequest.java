package com.bookleaf.portal.ai;

/**
 * A single, provider-neutral LLM request.
 *
 * @param purpose           logical task name, used for logging ({@code TRIAGE}, {@code DRAFT})
 * @param systemPrompt      instructions and rubric
 * @param userPrompt        the ticket-specific payload
 * @param maxOutputTokens   hard cap on the completion, to bound cost
 * @param jsonOutput        when true the provider is asked for a raw JSON object
 * @param temperature       sampling temperature; a deliberately low value keeps the
 *                          output stable and factual
 * @param bypassCache       when true the cached answer is neither read nor written.
 *                          Used by an explicit "regenerate" — a cached answer would
 *                          defeat the point of asking the model again.
 */
public record AiRequest(String purpose,
                         String systemPrompt,
                         String userPrompt,
                         int maxOutputTokens,
                         boolean jsonOutput,
                         double temperature,
                         boolean bypassCache) {

    public AiRequest(String purpose, String systemPrompt, String userPrompt,
                     int maxOutputTokens, boolean jsonOutput) {
        this(purpose, systemPrompt, userPrompt, maxOutputTokens, jsonOutput, 0.2, false);
    }

    public AiRequest withBypassCache() {
        return new AiRequest(purpose, systemPrompt, userPrompt, maxOutputTokens, jsonOutput, temperature, true);
    }

    public AiRequest withTemperature(double value) {
        return new AiRequest(purpose, systemPrompt, userPrompt, maxOutputTokens, jsonOutput, value, bypassCache);
    }
}
