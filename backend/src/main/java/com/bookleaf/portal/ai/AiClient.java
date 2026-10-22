package com.bookleaf.portal.ai;

/**
 * Adapter over a concrete LLM vendor.
 *
 * <p>Implementations translate the neutral {@link AiRequest}/{@link AiResult}
 * into the vendor's wire format and back, so the rest of the application never
 * depends on a specific vendor SDK.</p>
 */
public interface AiClient {

    String providerName();

    /**
     * @throws AiUnavailableException when the provider cannot serve the request
     */
    AiResult complete(AiRequest request);
}
