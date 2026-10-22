package com.bookleaf.portal.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookleaf.portal.config.AppProperties;
import com.bookleaf.portal.domain.AiCacheEntry;
import com.bookleaf.portal.repository.AiCacheEntryRepository;
import com.bookleaf.portal.repository.AiUsageLogRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests for the AI gateway's caching semantics.
 *
 * <p>The subtle case is "regenerate": the admin explicitly asks the model again,
 * so the cached answer must be neither read nor overwritten — the cached draft
 * stays as the canonical first answer and the fresh one is a one-off.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiGatewayCacheTest {

    @Mock
    private AnthropicAiClient anthropicClient;

    @Mock
    private OpenAiAiClient openAiClient;

    @Mock
    private AiUsageLogRepository usageLogRepository;

    @Mock
    private AiCacheEntryRepository cacheRepository;

    private AppProperties properties;
    private AiGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getAi().getAnthropic().setApiKey("test-key");
        properties.getAi().setProvider("anthropic");
        gateway = new AiGateway(properties, anthropicClient, openAiClient, usageLogRepository, cacheRepository);
    }

    private AiRequest request() {
        return new AiRequest("DRAFT", "system", "user", 700, false);
    }

    @Test
    @DisplayName("the first call reaches the provider and is cached")
    void firstCallIsCached() {
        when(cacheRepository.findByCacheKey(any())).thenReturn(Optional.empty());
        when(anthropicClient.complete(any())).thenReturn(new AiResult("first draft", 100, 20, "m", "anthropic", false));

        AiResult result = gateway.call(request());

        assertThat(result.text()).isEqualTo("first draft");
        assertThat(result.cached()).isFalse();
        verify(cacheRepository).save(any(AiCacheEntry.class));
    }

    @Test
    @DisplayName("a repeated call is served from the cache without calling the provider")
    void repeatCallIsCached() {
        when(cacheRepository.findByCacheKey(any()))
                .thenReturn(Optional.of(new AiCacheEntry("key", "DRAFT", "first draft")));

        AiResult result = gateway.call(request());

        assertThat(result.text()).isEqualTo("first draft");
        assertThat(result.cached()).isTrue();
        verify(anthropicClient, never()).complete(any());
    }

    @Test
    @DisplayName("bypassing the cache asks the provider again and does not overwrite the cached answer")
    void bypassCacheAsksAgainAndPreservesTheOriginal() {
        when(cacheRepository.findByCacheKey(any()))
                .thenReturn(Optional.of(new AiCacheEntry("key", "DRAFT", "first draft")));
        when(anthropicClient.complete(any()))
                .thenReturn(new AiResult("second draft", 100, 20, "m", "anthropic", false));

        AiResult result = gateway.call(request().withBypassCache());

        assertThat(result.text()).isEqualTo("second draft");
        assertThat(result.cached()).isFalse();
        verify(anthropicClient, times(1)).complete(any());
        // The cached original is left alone.
        verify(cacheRepository, never()).save(any(AiCacheEntry.class));
    }

    @Test
    @DisplayName("a bypassed call still records its token usage")
    void bypassedCallIsStillAccountedFor() {
        when(cacheRepository.findByCacheKey(any())).thenReturn(Optional.empty());
        when(anthropicClient.complete(any()))
                .thenReturn(new AiResult("fresh", 100, 20, "m", "anthropic", false));

        gateway.call(request().withBypassCache());

        verify(usageLogRepository).save(any(com.bookleaf.portal.domain.AiUsageLog.class));
    }
}
