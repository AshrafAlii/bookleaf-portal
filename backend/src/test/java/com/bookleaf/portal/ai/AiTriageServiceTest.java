package com.bookleaf.portal.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookleaf.portal.config.AppProperties;
import com.bookleaf.portal.domain.DecisionSource;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests for the AI triage path with a stubbed gateway.
 *
 * <p>These cover the parts that only matter when a real model answers: parsing the
 * structured output, validating it against the known vocabulary, and falling back
 * to the deterministic rules whenever the model misbehaves.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiTriageServiceTest {

    @Mock
    private AiGateway gateway;

    @Mock
    private HeuristicTriageService heuristicTriageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiTriageService service() {
        AppProperties properties = new AppProperties();
        return new AiTriageService(gateway, heuristicTriageService, properties, objectMapper);
    }

    @Test
    @DisplayName("a well-formed model response is used as the decision")
    void parsesValidResponse() {
        when(gateway.isAiEnabled()).thenReturn(true);
        when(gateway.call(any())).thenReturn(new AiResult(
                "{\"category\":\"ROYALTY_PAYMENTS\",\"priority\":\"CRITICAL\","
                        + "\"rationale\":\"Author states no royalty for 6 months.\",\"signals\":[\"6 months\"]}",
                700, 50, "test-model", "stub", false));

        TriageResult result = service().triage("Royalty not received",
                "I have not received any royalty for 6 months", "Whispers of the Ganges", "Author: A");

        assertThat(result.category()).isEqualTo(TicketCategory.ROYALTY_PAYMENTS);
        assertThat(result.priority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(result.source()).isEqualTo(DecisionSource.AI);
        assertThat(result.rationale()).contains("6 months");
        assertThat(result.note()).isNull();
    }

    @Test
    @DisplayName("a response wrapped in a markdown fence is still parsed")
    void parsesFencedJson() {
        when(gateway.isAiEnabled()).thenReturn(true);
        when(gateway.call(any())).thenReturn(new AiResult(
                "```json\n{\"category\":\"PRINTING_QUALITY\",\"priority\":\"HIGH\",\"rationale\":\"Defective copies.\"}\n```",
                700, 50, "test-model", "stub", false));

        TriageResult result = service().triage("Print quality", "Blurry pages", null, "");

        assertThat(result.category()).isEqualTo(TicketCategory.PRINTING_QUALITY);
        assertThat(result.priority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    @DisplayName("an unknown category falls back to the deterministic rules")
    void unknownCategoryFallsBack() {
        when(gateway.isAiEnabled()).thenReturn(true);
        when(gateway.call(any())).thenReturn(new AiResult(
                "{\"category\":\"PAYMENTS_AND_MONEY\",\"priority\":\"HIGH\",\"rationale\":\"...\"}",
                700, 50, "test-model", "stub", false));
        when(heuristicTriageService.classify(any(), any()))
                .thenReturn(TriageResult.of(TicketCategory.ROYALTY_PAYMENTS, TicketPriority.MEDIUM,
                        "rule match", DecisionSource.HEURISTIC, java.util.List.of("royalty"), null));

        TriageResult result = service().triage("Royalty", "My royalty is late", null, "");

        assertThat(result.source()).isEqualTo(DecisionSource.HEURISTIC);
        assertThat(result.category()).isEqualTo(TicketCategory.ROYALTY_PAYMENTS);
        assertThat(result.note()).contains("could not be parsed");
    }

    @Test
    @DisplayName("unparseable output falls back to the deterministic rules")
    void garbageFallsBack() {
        when(gateway.isAiEnabled()).thenReturn(true);
        when(gateway.call(any())).thenReturn(new AiResult("I'm sorry, I can't help with that.", 10, 5,
                "test-model", "stub", false));
        when(heuristicTriageService.classify(any(), any()))
                .thenReturn(TriageResult.of(TicketCategory.GENERAL_INQUIRY, TicketPriority.LOW,
                        "rule match", DecisionSource.HEURISTIC, java.util.List.of(), null));

        TriageResult result = service().triage("Hello", "Hello there", null, "");

        assertThat(result.source()).isEqualTo(DecisionSource.HEURISTIC);
        assertThat(result.note()).isNotBlank();
    }

    @Test
    @DisplayName("a provider outage falls back to the deterministic rules")
    void providerOutageFallsBack() {
        when(gateway.isAiEnabled()).thenReturn(true);
        when(gateway.call(any())).thenThrow(new AiUnavailableException("provider down"));
        when(heuristicTriageService.classify(any(), any()))
                .thenReturn(TriageResult.of(TicketCategory.PRINTING_QUALITY, TicketPriority.HIGH,
                        "rule match", DecisionSource.HEURISTIC, java.util.List.of("print"), null));

        TriageResult result = service().triage("Print quality", "Blurry pages", null, "");

        assertThat(result.source()).isEqualTo(DecisionSource.HEURISTIC);
        assertThat(result.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(result.note()).contains("unavailable");
    }

    @Test
    @DisplayName("with no provider configured the deterministic rules decide")
    void noProviderUsesRules() {
        when(gateway.isAiEnabled()).thenReturn(false);
        when(heuristicTriageService.classify(any(), any()))
                .thenReturn(TriageResult.of(TicketCategory.GENERAL_INQUIRY, TicketPriority.LOW,
                        "rule match", DecisionSource.HEURISTIC, java.util.List.of(), null));

        TriageResult result = service().triage("Hello", "Hello there", null, "");

        assertThat(result.source()).isEqualTo(DecisionSource.HEURISTIC);
        assertThat(result.note()).contains("No AI provider configured");
    }
}
