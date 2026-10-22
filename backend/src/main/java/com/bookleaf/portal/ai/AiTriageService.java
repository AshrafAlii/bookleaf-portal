package com.bookleaf.portal.ai;

import com.bookleaf.portal.config.AppProperties;
import com.bookleaf.portal.domain.DecisionSource;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Classifies a new ticket and scores its priority.
 *
 * <p><b>One call, two decisions.</b> Category and priority are requested in a
 * single structured-output request rather than two separate ones. That halves the
 * number of round trips and prompt tokens for triage, which matters because
 * every new ticket triggers a classification.</p>
 *
 * <p><b>Always answers.</b> If the provider is down, rate limited or returns
 * something unusable, the deterministic {@link HeuristicTriageService} takes over
 * and the ticket is still created with a sensible category and priority. The
 * source of the decision is recorded so the admin UI can show it.</p>
 */
@Service
public class AiTriageService {

    private static final Logger log = LoggerFactory.getLogger(AiTriageService.class);

    private final AiGateway gateway;
    private final HeuristicTriageService heuristicTriageService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public AiTriageService(AiGateway gateway,
                           HeuristicTriageService heuristicTriageService,
                           AppProperties properties,
                           ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.heuristicTriageService = heuristicTriageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * The deterministic classification, with no model involved.
     *
     * <p>Used as the instant first answer for a new ticket, so the author never
     * waits on the model and the request thread is never blocked on it.</p>
     */
    public TriageResult classifyWithoutAi(String subject, String description) {
        TriageResult result = heuristicTriageService.classify(subject, description);
        return new TriageResult(result.category(), result.priority(), result.rationale(),
                result.source(), result.signals(), null);
    }

    public TriageResult triage(String subject, String description, String bookLabel, String authorContext) {
        if (!gateway.isAiEnabled()) {
            TriageResult fallback = heuristicTriageService.classify(subject, description);
            return new TriageResult(fallback.category(), fallback.priority(), fallback.rationale(),
                    fallback.source(), fallback.signals(),
                    "No AI provider configured — classified with the built-in fallback rules.");
        }

        AiRequest request = new AiRequest(
                "TRIAGE",
                Prompts.TRIAGE_SYSTEM,
                Prompts.triageUser(bookLabel, subject, description, authorContext),
                properties.getAi().getTriageMaxOutputTokens(),
                true);

        try {
            AiResult result = gateway.call(request);
            Optional<TriageResult> parsed = parse(result.text());
            if (parsed.isPresent()) {
                return parsed.get();
            }
            log.warn("AI triage returned an unusable payload, falling back to rules");
            TriageResult fallback = heuristicTriageService.classify(subject, description);
            return new TriageResult(fallback.category(), fallback.priority(), fallback.rationale(),
                    fallback.source(), fallback.signals(),
                    "The AI response could not be parsed — classified with the built-in fallback rules.");
        } catch (AiUnavailableException ex) {
            log.warn("AI triage unavailable, falling back to rules: {}", ex.getMessage());
            TriageResult fallback = heuristicTriageService.classify(subject, description);
            return new TriageResult(fallback.category(), fallback.priority(), fallback.rationale(),
                    fallback.source(), fallback.signals(),
                    "The AI service was unavailable — classified with the built-in fallback rules.");
        }
    }

    private Optional<TriageResult> parse(String raw) {
        try {
            String json = extractJsonObject(raw);
            if (json == null) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(json);

            TicketCategory category = parseCategory(node.path("category").asText());
            TicketPriority priority = parsePriority(node.path("priority").asText());
            if (category == null || priority == null) {
                return Optional.empty();
            }
            String rationale = node.path("rationale").asText("").strip();
            if (rationale.isEmpty()) {
                rationale = "Categorised as " + category.label() + " with " + priority.label() + " priority.";
            }
            List<String> signals = new ArrayList<>();
            for (JsonNode signal : node.path("signals")) {
                signals.add(signal.asText());
            }
            return Optional.of(TriageResult.of(category, priority, truncate(rationale, 500),
                    DecisionSource.AI, signals, null));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /** Models sometimes wrap JSON in a markdown fence; strip it. */
    private String extractJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (newline > 0 && end > newline) {
                text = text.substring(newline + 1, end).strip();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private TicketCategory parseCategory(String value) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (TicketCategory category : TicketCategory.values()) {
            if (category.name().equals(normalised)) {
                return category;
            }
        }
        return null;
    }

    private TicketPriority parsePriority(String value) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        for (TicketPriority priority : TicketPriority.values()) {
            if (priority.name().equals(normalised)) {
                return priority;
            }
        }
        return null;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
