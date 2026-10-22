package com.bookleaf.portal.ai;

import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketEvent;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.service.TicketCommandService;
import com.bookleaf.portal.service.TicketEventPublisher;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Refines a ticket's classification in the background.
 *
 * <p>The ticket is created immediately with the deterministic classification, so
 * the author is never waiting on the model, and the request thread is never
 * blocked on it. The AI then refines the category and priority in the
 * background; if it disagrees, the ticket is updated, an audit event is written
 * and an event is published so the admin queue updates live.</p>
 *
 * <p>For a fast hosted model the difference is invisible. For a model running on
 * the same machine as the app it is the difference between a responsive portal
 * and one that hangs for half a minute every time a ticket is raised.</p>
 */
@Service
public class AsyncTriageService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTriageService.class);

    private final ThreadPoolTaskExecutor executor;
    private final AiTriageService triageService;
    private final TicketCommandService commandService;
    private final TicketEventPublisher eventPublisher;

    public AsyncTriageService(@Qualifier("aiTriageExecutor") ThreadPoolTaskExecutor executor,
                               AiTriageService triageService,
                               TicketCommandService commandService,
                               TicketEventPublisher eventPublisher) {
        this.executor = executor;
        this.triageService = triageService;
        this.commandService = commandService;
        this.eventPublisher = eventPublisher;
    }

    public void refine(UUID ticketId,
                        String subject,
                        String description,
                        String bookLabel,
                        String authorContext) {
        executor.execute(() -> {
            try {
                Ticket ticket = commandService.loadForAdmin(ticketId);
                TriageResult result = triageService.triage(subject, description, bookLabel, authorContext);

                if (result.source() != com.bookleaf.portal.domain.DecisionSource.AI) {
                    // The provider was unavailable — the deterministic
                    // classification stands and the ticket is left alone.
                    commandService.recordEvent(ticket, null, TicketEvent.AI_CLASSIFIED,
                            "AI refinement unavailable: " + (result.note() == null ? "unknown reason" : result.note()));
                    return;
                }

                boolean changed = ticket.getCategory() != result.category()
                        || ticket.getPriority() != result.priority();
                if (!changed) {
                    commandService.recordEvent(ticket, null, TicketEvent.AI_CLASSIFIED,
                            "AI confirmed " + result.category().label() + " / " + result.priority().label());
                    return;
                }

                String ticketCode = ticket.getTicketCode();
                Ticket updated = commandService.applyAiDecision(ticket, result.category(), result.priority(),
                        result.rationale());
                eventPublisher.publish(updated, "AI_CLASSIFIED",
                        ticketCode + " classified as " + result.category().label()
                                + " (" + result.priority().label() + ") by the AI");
            } catch (Exception ex) {
                log.warn("Background triage failed for ticket {}: {}", ticketId, ex.getMessage());
            }
        });
    }
}
