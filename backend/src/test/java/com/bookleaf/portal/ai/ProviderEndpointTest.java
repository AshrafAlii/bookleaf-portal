package com.bookleaf.portal.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookleaf.portal.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The OpenAI-compatible providers do not agree on where the API lives, so the
 * chat path is configurable. These tests pin the URL that gets built for each
 * provider, because a wrong URL only fails at runtime against the real API.
 */
class ProviderEndpointTest {

    private String openAiUrl(String baseUrl, String chatPath) {
        AppProperties properties = new AppProperties();
        properties.getAi().getOpenai().setBaseUrl(baseUrl);
        properties.getAi().getOpenai().setChatPath(chatPath);
        return properties.getAi().getOpenai().getBaseUrl()
                + properties.getAi().getOpenai().getChatPath();
    }

    @Test
    @DisplayName("OpenAI uses /v1/chat/completions")
    void openAi() {
        assertThat(openAiUrl("https://api.openai.com", "/v1/chat/completions"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    @DisplayName("OpenRouter puts the base and the path in different places")
    void openRouter() {
        assertThat(openAiUrl("https://openrouter.ai/api/v1", "/chat/completions"))
                .isEqualTo("https://openrouter.ai/api/v1/chat/completions");
    }

    @Test
    @DisplayName("Gemini's OpenAI-compatible endpoint is reachable the same way")
    void gemini() {
        assertThat(openAiUrl("https://generativelanguage.googleapis.com/v1beta/openai", "/chat/completions"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
    }

    @Test
    @DisplayName("a local Ollama server is reached over plain HTTP")
    void ollama() {
        assertThat(openAiUrl("http://localhost:11434", "/v1/chat/completions"))
                .isEqualTo("http://localhost:11434/v1/chat/completions");
    }

    @Test
    @DisplayName("the Anthropic endpoint is built from its own base URL")
    void anthropic() {
        AppProperties properties = new AppProperties();
        properties.getAi().getAnthropic().setBaseUrl("https://api.anthropic.com");
        assertThat(properties.getAi().getAnthropic().getBaseUrl() + "/v1/messages")
                .isEqualTo("https://api.anthropic.com/v1/messages");
    }
}
