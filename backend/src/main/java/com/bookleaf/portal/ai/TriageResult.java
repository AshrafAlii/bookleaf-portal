package com.bookleaf.portal.ai;

import com.bookleaf.portal.domain.DecisionSource;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import java.util.List;

/**
 * Outcome of classifying a ticket.
 *
 * @param source where the decision came from — AI, the heuristic fallback, or a
 *               human override
 * @param note    short explanation shown in the admin UI when the AI was unavailable
 */
public record TriageResult(TicketCategory category,
                            TicketPriority priority,
                            String rationale,
                            DecisionSource source,
                            List<String> signals,
                            String note) {

    public static TriageResult of(TicketCategory category, TicketPriority priority, String rationale,
                                  DecisionSource source, List<String> signals, String note) {
        return new TriageResult(category, priority, rationale, source, signals == null ? List.of() : signals, note);
    }
}
