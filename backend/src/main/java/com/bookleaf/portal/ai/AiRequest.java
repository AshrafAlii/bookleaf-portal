package com.bookleaf.portal.ai;

/**
 * A single, provider-neutral LLM request.
 *
 * @param purpose           logical task name, used for logging ({@code TRIAGE}, {@code DRAFT})
 * @param systemPrompt      instructions and rubric
 * @param userPrompt        the ticket-specific payload
 * @param maxOutputTokens   hard cap on the completion, to bound cost
 * @param jsonOutput        when true the provider is asked for a raw JSON object
 */
public record AiRequest(String purpose,
                         String systemPrompt,
                         String userPrompt,
                         int maxOutputTokens,
                         boolean jsonOutput) {
}
