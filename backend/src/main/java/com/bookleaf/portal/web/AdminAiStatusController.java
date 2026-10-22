package com.bookleaf.portal.web;

import com.bookleaf.portal.ai.AiGateway;
import com.bookleaf.portal.ai.AiRequest;
import com.bookleaf.portal.ai.AiResult;
import com.bookleaf.portal.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI wiring diagnostics.
 *
 * <p>Answers the first question anyone asks about a deployment: <em>is the AI
 * actually connected, and to what?</em> It reports the resolved provider, model
 * and endpoint (never the key), and can make a live one-token call to prove the
 * provider is reachable from this machine.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/ai-status")
@Tag(name = "Admin AI status", description = "Diagnose the AI connection on a deployed instance")
@SecurityRequirement(name = "bearerAuth")
public class AdminAiStatusController {

    private final AiGateway aiGateway;
    private final AppProperties properties;

    public AdminAiStatusController(AiGateway aiGateway, AppProperties properties) {
        this.aiGateway = aiGateway;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Report the AI provider, model, endpoint and reachability")
    public AiStatus status() {
        String provider = aiGateway.providerName();
        boolean enabled = aiGateway.isAiEnabled();

        AiStatus status = new AiStatus(
                enabled,
                provider,
                aiGateway.modelName("DRAFT"),
                endpointFor(provider),
                properties.getAi().getTriageMaxOutputTokens(),
                properties.getAi().getDraftMaxOutputTokens(),
                properties.getAi().getMaxConcurrentCalls(),
                properties.getAi().isTriageAsync(),
                null,
                null);

        if (!enabled) {
            return status.withCheck("No AI provider is configured. Tickets are classified with the "
                    + "built-in rules and replies are written by hand.");
        }

        Instant started = Instant.now();
        try {
            java.util.Optional<AiResult> result = aiGateway.callQuietly(new AiRequest(
                    "HEALTH",
                    "Reply with the single word: ok",
                    "ok",
                    5,
                    false));
            long latency = Duration.between(started, Instant.now()).toMillis();
            if (result.isPresent()) {
                return status.withCheck("Reachable in " + latency + " ms.", latency);
            }
            return status.withCheck("Configured but the provider did not answer. "
                    + "Check the key, the endpoint and the model name.");
        } catch (Exception ex) {
            return status.withCheck("The check failed: " + ex.getMessage());
        }
    }

    /** The URL the client will actually call — the key is never included. */
    private String endpointFor(String provider) {
        if ("anthropic".equals(provider)) {
            return properties.getAi().getAnthropic().getBaseUrl() + "/v1/messages";
        }
        if ("openai".equals(provider)) {
            return properties.getAi().getOpenai().getBaseUrl()
                    + properties.getAi().getOpenai().getChatPath();
        }
        return null;
    }

    public record AiStatus(boolean enabled,
                           String provider,
                           String model,
                           String endpoint,
                           int triageMaxOutputTokens,
                           int draftMaxOutputTokens,
                           int maxConcurrentCalls,
                           boolean triageAsync,
                           String message,
                           Long latencyMs) {

        AiStatus withCheck(String message) {
            return new AiStatus(enabled, provider, model, endpoint, triageMaxOutputTokens,
                    draftMaxOutputTokens, maxConcurrentCalls, triageAsync, message, latencyMs);
        }

        AiStatus withCheck(String message, long latencyMs) {
            return new AiStatus(enabled, provider, model, endpoint, triageMaxOutputTokens,
                    draftMaxOutputTokens, maxConcurrentCalls, triageAsync, message, latencyMs);
        }
    }
}
