package com.bookleaf.portal.ai;

import com.bookleaf.portal.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic (Claude) adapter using the Messages API.
 *
 * <p>Talks to the REST API directly rather than through the vendor SDK: it keeps
 * the dependency footprint small, gives exact control over the payload, and makes
 * the provider boundary obvious in the code.</p>
 */
@Component
public class AnthropicAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiClient.class);

    private final AppProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AnthropicAiClient(AppProperties properties, RestClient.Builder restClientBuilder,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public AiResult complete(AiRequest request) {
        AppProperties.Ai.Anthropic config = properties.getAi().getAnthropic();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new AiUnavailableException("ANTHROPIC_API_KEY is not configured");
        }

        String model = "DRAFT".equals(request.purpose())
                ? properties.getAi().draftModelName()
                : properties.getAi().triageModelName();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", request.maxOutputTokens());
        payload.put("temperature", request.temperature());
        payload.put("system", request.systemPrompt());
        payload.put("messages", List.of(Map.of("role", "user", "content", request.userPrompt())));

        try {
            JsonNode response = restClient.post()
                    .uri(config.getBaseUrl() + "/v1/messages")
                    .header("x-api-key", config.getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new AiUnavailableException("Anthropic returned an empty response");
            }
            String text = extractText(response);
            if (text.isBlank()) {
                throw new AiUnavailableException("Anthropic response contained no text");
            }
            JsonNode usage = response.path("usage");
            return new AiResult(text,
                    usage.path("input_tokens").asInt(0),
                    usage.path("output_tokens").asInt(0),
                    model,
                    providerName(),
                    false);
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Anthropic call failed: {}", ex.getMessage());
            throw new AiUnavailableException("Anthropic call failed: " + ex.getMessage(), ex);
        }
    }

    private String extractText(JsonNode response) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString().strip();
    }

    /** Exposed for the admin health panel. */
    public List<String> supportedModels() {
        return List.of(properties.getAi().getAnthropic().getModel());
    }
}
