package com.bookleaf.portal.ai;

import com.bookleaf.portal.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI (GPT-4o-mini / GPT-4o) adapter using the Chat Completions API.
 *
 * <p>Present as a first-class alternative provider: swapping the model is a
 * configuration change, not a code change.</p>
 */
@Component
public class OpenAiAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAiClient.class);

    private final AppProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiAiClient(AppProperties properties, RestClient.Builder restClientBuilder,
                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public AiResult complete(AiRequest request) {
        AppProperties.Ai.OpenAi config = properties.getAi().getOpenai();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new AiUnavailableException("OPENAI_API_KEY is not configured");
        }

        String model = "DRAFT".equals(request.purpose())
                ? properties.getAi().draftModelName()
                : properties.getAi().triageModelName();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("max_tokens", request.maxOutputTokens());
        payload.put("temperature", request.temperature());
        if (request.jsonOutput()) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        try {
            JsonNode response = restClient.post()
                    .uri(config.getBaseUrl() + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new AiUnavailableException("OpenAI returned an empty response");
            }
            String text = response.path("choices").path(0).path("message").path("content").asText("").strip();
            if (text.isBlank()) {
                throw new AiUnavailableException("OpenAI response contained no content");
            }
            JsonNode usage = response.path("usage");
            return new AiResult(text,
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    model,
                    providerName(),
                    false);
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OpenAI call failed: {}", ex.getMessage());
            throw new AiUnavailableException("OpenAI call failed: " + ex.getMessage(), ex);
        }
    }

    /** Exposed for the admin health panel. */
    public List<String> supportedModels() {
        return List.of(properties.getAi().getOpenai().getModel());
    }
}
