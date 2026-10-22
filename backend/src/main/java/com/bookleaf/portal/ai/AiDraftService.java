package com.bookleaf.portal.ai;

import com.bookleaf.portal.config.AppProperties;
import com.bookleaf.portal.domain.Ticket;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generates a draft reply for an admin, grounded in the BookLeaf knowledge base
 * and live account data.
 *
 * <p><b>Prompt budget.</b> The full knowledge base is ~1,200 tokens. Instead of
 * pasting it into every prompt we retrieve the 2–3 most relevant sections
 * (~350 tokens) and always include the short tone guide. The calibration example
 * for the ticket's category is included separately so the model mirrors the
 * expected structure. The final prompt is typically under 900 tokens — a
 * meaningful saving across a queue of hundreds of tickets.</p>
 *
 * <p><b>Degradation.</b> If the provider fails, the method returns
 * {@link DraftResult#unavailable} rather than throwing. The admin still gets a
 * fully working ticket screen and writes the reply by hand.</p>
 */
@Service
public class AiDraftService {

    private static final Logger log = LoggerFactory.getLogger(AiDraftService.class);

    private final AiGateway gateway;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AppProperties properties;

    public AiDraftService(AiGateway gateway, KnowledgeBaseService knowledgeBaseService, AppProperties properties) {
        this.gateway = gateway;
        this.knowledgeBaseService = knowledgeBaseService;
        this.properties = properties;
    }

    public DraftResult generate(Ticket ticket, String authorContext) {
        String ticketText = ticket.getSubject() + " " + ticket.getDescription();
        KnowledgeBaseService.Retrieval retrieval =
                knowledgeBaseService.retrieve(ticketText, properties.getAi().getKnowledgeTopSections());

        // Policy context excludes calibration examples — they are passed separately.
        String policyContext = retrieval.sections().stream()
                .filter(section -> !section.title().toLowerCase().startsWith("example - "))
                .map(section -> "### " + section.title() + "\n" + section.body())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        List<String> calibrationBlocks = knowledgeBaseService
                .calibrationExamples(ticket.getCategory().label())
                .stream()
                .map(section -> section.title() + "\n" + section.body())
                .toList();
        String calibration = Prompts.calibrationBlock(ticket.getCategory().label(), calibrationBlocks);

        String ticketBlock = """
                TICKET
                Reference: %s
                Category: %s
                Priority: %s
                Book: %s
                Subject: %s
                Description:
                %s
                """.formatted(ticket.getTicketCode(),
                ticket.getCategory().label(),
                ticket.getPriority().label(),
                ticket.getBook() == null ? "General / Account Level" : ticket.getBook().getTitle(),
                ticket.getSubject(),
                ticket.getDescription());

        AiRequest request = new AiRequest(
                "DRAFT",
                Prompts.DRAFT_SYSTEM,
                Prompts.draftUser(ticketBlock, authorContext, policyContext, calibration),
                properties.getAi().getDraftMaxOutputTokens(),
                false);

        if (!gateway.isAiEnabled()) {
            return DraftResult.unavailable("none", "none", retrieval.tokens(),
                    "No AI provider is configured, so no draft was generated. "
                            + "Write the reply manually — the ticket is fully usable.");
        }

        try {
            AiResult result = gateway.call(request);
            return new DraftResult(true, result.text().strip(), result.provider(), result.model(),
                    retrieval.tokens(), result.cached()
                    ? "Served from the response cache — no additional provider cost."
                    : null);
        } catch (AiUnavailableException ex) {
            log.warn("Draft generation failed: {}", ex.getMessage());
            return DraftResult.unavailable(gateway.providerName(), gateway.modelName("DRAFT"),
                    retrieval.tokens(),
                    "The AI service could not be reached (" + ex.getMessage() + "). "
                            + "Write the reply manually — the ticket is fully usable.");
        }
    }
}
