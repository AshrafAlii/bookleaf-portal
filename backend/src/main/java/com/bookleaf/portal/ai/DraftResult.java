package com.bookleaf.portal.ai;

import com.bookleaf.portal.config.AppProperties;
import com.bookleaf.portal.domain.Ticket;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Outcome of an AI draft request.
 *
 * @param available    false when the provider failed — the admin writes manually
 * @param body         the drafted reply (null when unavailable)
 * @param provider     which provider produced the draft
 * @param model        which model produced the draft
 * @param contextTokens approximate tokens of retrieved knowledge-base context
 * @param note         human-readable explanation shown in the admin UI
 */
public record DraftResult(boolean available,
                          String body,
                          String provider,
                          String model,
                          int contextTokens,
                          String note) {

    public static DraftResult unavailable(String provider, String model, int contextTokens, String note) {
        return new DraftResult(false, null, provider, model, contextTokens, note);
    }
}
