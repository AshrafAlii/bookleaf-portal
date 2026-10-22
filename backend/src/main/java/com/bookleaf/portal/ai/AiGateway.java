package com.bookleaf.portal.ai;

import com.bookleaf.portal.config.AppProperties;
import com.bookleaf.portal.domain.AiCacheEntry;
import com.bookleaf.portal.domain.AiUsageLog;
import com.bookleaf.portal.repository.AiCacheEntryRepository;
import com.bookleaf.portal.repository.AiUsageLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single entry point to the LLM layer.
 *
 * <p>It owns three concerns that every AI feature shares:</p>
 * <ol>
 *   <li><b>Provider selection</b> — Anthropic or OpenAI, chosen from configuration.
 *       If neither key is present the portal runs in "no AI" mode and callers
 *       fall back to deterministic heuristics.</li>
 *   <li><b>Caching</b> — results are stored under a hash of (purpose, model, prompt).
 *       Because the retrieved knowledge base is embedded in the prompt, editing the
 *       knowledge base automatically invalidates every cached answer.</li>
 *   <li><b>Usage accounting</b> — every call (cached or not) is recorded with
 *       tokens and latency so the admin view can show real cost.</li>
 * </ol>
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final AppProperties properties;
    private final AnthropicAiClient anthropicClient;
    private final OpenAiAiClient openAiClient;
    private final AiUsageLogRepository usageLogRepository;
    private final AiCacheEntryRepository cacheRepository;

    public AiGateway(AppProperties properties,
                     AnthropicAiClient anthropicClient,
                     OpenAiAiClient openAiClient,
                     AiUsageLogRepository usageLogRepository,
                     AiCacheEntryRepository cacheRepository) {
        this.properties = properties;
        this.anthropicClient = anthropicClient;
        this.openAiClient = openAiClient;
        this.usageLogRepository = usageLogRepository;
        this.cacheRepository = cacheRepository;
    }

    /** {@code true} when a real LLM provider is configured and reachable-by-config. */
    public boolean isAiEnabled() {
        return !"none".equals(properties.getAi().resolvedProvider());
    }

    public String providerName() {
        return properties.getAi().resolvedProvider();
    }

    public String modelName(String purpose) {
        if (!isAiEnabled()) {
            return null;
        }
        return "DRAFT".equals(purpose)
                ? properties.getAi().draftModelName()
                : properties.getAi().triageModelName();
    }

    /**
     * Executes a request, serving from cache when possible.
     *
     * @throws AiUnavailableException if no provider is configured or the provider fails
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiResult call(AiRequest request) {
        String provider = properties.getAi().resolvedProvider();
        if ("none".equals(provider)) {
            throw new AiUnavailableException("No AI provider configured (set ANTHROPIC_API_KEY or OPENAI_API_KEY)");
        }

        String model = modelName(request.purpose());
        String cacheKey = cacheKey(request.purpose(), model, request.systemPrompt(), request.userPrompt());

        Optional<AiCacheEntry> cached = cacheRepository.findByCacheKey(cacheKey);
        if (cached.isPresent()) {
            AiUsageLog usageLog = new AiUsageLog(request.purpose(), provider, model, 0, 0, true, 0, true, null);
            usageLogRepository.save(usageLog);
            return AiResult.cachedResult(cached.get().getPayload(), model, provider);
        }

        AiClient client = "anthropic".equals(provider) ? anthropicClient : openAiClient;
        long startedAt = System.currentTimeMillis();
        try {
            AiResult result = client.complete(request);
            int latency = (int) (System.currentTimeMillis() - startedAt);
            usageLogRepository.save(new AiUsageLog(request.purpose(), provider, result.model(),
                    result.promptTokens(), result.completionTokens(), false, latency, true, null));
            cacheRepository.save(new AiCacheEntry(cacheKey, request.purpose(), result.text()));
            return result;
        } catch (AiUnavailableException ex) {
            int latency = (int) (System.currentTimeMillis() - startedAt);
            usageLogRepository.save(new AiUsageLog(request.purpose(), provider, model, null, null, false,
                    latency, false, truncate(ex.getMessage())));
            log.warn("AI call failed for purpose {}: {}", request.purpose(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Convenience wrapper used where a failure must not break the caller.
     *
     * @return the result, or empty when the provider is unavailable
     */
    public Optional<AiResult> callQuietly(AiRequest request) {
        try {
            return Optional.of(call(request));
        } catch (AiUnavailableException ex) {
            return Optional.empty();
        }
    }

    /**
     * Cache key over the full request.
     *
     * <p>Both the system and the user prompt are included: changing a prompt must
     * invalidate the cached answers it produced, otherwise a prompt fix would
     * silently keep serving the old results. The user prompt already contains the
     * retrieved knowledge base, so editing the knowledge base invalidates the
     * cache for free.</p>
     */
    private String cacheKey(String purpose, String model, String systemPrompt, String userPrompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((purpose + "\n" + model + "\n" + systemPrompt + "\n" + userPrompt)
                    .getBytes(StandardCharsets.UTF_8));
            return purpose.toLowerCase() + ":" + HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 590 ? message.substring(0, 590) : message;
    }
}
