package com.bookleaf.portal.service;

import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.Book;
import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketEvent;
import com.bookleaf.portal.domain.TicketMessage;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.domain.TicketStatus;
import com.bookleaf.portal.domain.User;
import com.bookleaf.portal.dto.TicketDtos.CreateTicketRequest;
import com.bookleaf.portal.dto.TicketDtos.ReplyRequest;
import com.bookleaf.portal.repository.AuthorRepository;
import com.bookleaf.portal.repository.BookRepository;
import com.bookleaf.portal.repository.TicketEventRepository;
import com.bookleaf.portal.repository.TicketMessageRepository;
import com.bookleaf.portal.repository.TicketRepository;
import com.bookleaf.portal.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every write to the ticket aggregate.
 *
 * <p>Kept separate from {@link TicketService} for two reasons: writes are
 * short, transactional units, while the orchestration layer calls out to the LLM
 * and must <em>not</em> hold a database transaction open across a network call
 * to a third party.</p>
 */
@Service
public class TicketCommandService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketEventRepository eventRepository;
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public TicketCommandService(TicketRepository ticketRepository,
                                TicketMessageRepository messageRepository,
                                TicketEventRepository eventRepository,
                                AuthorRepository authorRepository,
                                BookRepository bookRepository,
                                UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    /**
     * Applies an AI classification to a ticket that was created with the
     * deterministic fallback classification.
     */
    @Transactional
    public Ticket applyAiDecision(Ticket ticket, TicketCategory category, TicketPriority priority,
                                  String rationale) {
        ticket.applyDecision(category, priority, rationale, com.bookleaf.portal.domain.DecisionSource.AI);
        eventRepository.save(new TicketEvent(ticket, null, TicketEvent.AI_CLASSIFIED,
                "Refined by the AI to " + category.label() + " / " + priority.label()
                        + (rationale == null ? "" : " — " + rationale)));
        return ticketRepository.save(ticket);
    }

    /**
     * Loads a ticket for a write operation. Runs inside its own transaction so
     * the entity and its associations are fully initialised before the caller
     * works with it.
     */
    @Transactional
    public Ticket loadForAdmin(UUID ticketId) {
        return ticketRepository.findDetailedById(ticketId)
                .orElseThrow(() -> com.bookleaf.portal.exception.ApiException.notFound("Ticket not found"));
    }

    /**
     * Loads a ticket and asserts it belongs to the given author.
     * Returns 404 (not 403) for a mismatch so an author cannot probe for the
     * existence of other authors' tickets.
     */
    @Transactional
    public Ticket loadForAuthor(UUID ticketId, UUID authorId) {
        Ticket ticket = loadForAdmin(ticketId);
        if (!ticket.getAuthor().getId().equals(authorId)) {
            throw com.bookleaf.portal.exception.ApiException.notFound("Ticket not found");
        }
        return ticket;
    }

    @Transactional
    public Ticket persistNewTicket(UUID authorId,
                                    CreateTicketRequest request,
                                    TicketCategory category,
                                    TicketPriority priority,
                                    String rationale,
                                    com.bookleaf.portal.domain.DecisionSource source) {
        Author author = authorRepository.findById(authorId)
                .orElseThrow(() -> com.bookleaf.portal.exception.ApiException.notFound("Author not found"));

        Book book = null;
        if (request.bookId() != null) {
            book = bookRepository.findById(request.bookId())
                    .filter(candidate -> candidate.getAuthor().getId().equals(authorId))
                    .orElseThrow(() -> com.bookleaf.portal.exception.ApiException.badRequest(
                            "The selected book does not belong to your account"));
        }

        long sequence = ticketRepository.nextTicketSequence();
        Ticket ticket = new Ticket(String.format("BL-%06d", sequence), author, book,
                request.subject().trim(), request.description().trim(),
                request.attachmentName() == null || request.attachmentName().isBlank()
                        ? null : request.attachmentName().trim());
        ticket.applyDecision(category, priority, rationale, source);
        Ticket saved = ticketRepository.save(ticket);

        eventRepository.save(new TicketEvent(saved, author.getUser(), TicketEvent.CREATED,
                "Ticket raised" + (book == null ? "" : " about " + book.getTitle())));

        return saved;
    }

    @Transactional
    public Ticket changeStatus(Ticket ticket, TicketStatus newStatus, User actor) {
        TicketStatus previous = ticket.getStatus();
        ticket.changeStatus(newStatus);
        ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket, actor, TicketEvent.STATUS_CHANGED,
                previous.label() + " → " + newStatus.label()));
        return ticket;
    }

    @Transactional
    public Ticket overrideCategory(Ticket ticket, TicketCategory category, User actor) {
        TicketCategory previous = ticket.getCategory();
        ticket.overrideCategory(category);
        ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket, actor, TicketEvent.CATEGORY_OVERRIDDEN,
                previous.label() + " → " + category.label() + " (manual override of AI classification)"));
        return ticket;
    }

    @Transactional
    public Ticket overridePriority(Ticket ticket, TicketPriority priority, User actor) {
        TicketPriority previous = ticket.getPriority();
        ticket.overridePriority(priority);
        ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket, actor, TicketEvent.PRIORITY_OVERRIDDEN,
                previous.label() + " → " + priority.label() + " (manual override)"));
        return ticket;
    }

    @Transactional
    public Ticket assignTo(Ticket ticket, User admin) {
        String previous = ticket.getAssignedTo() == null ? "Unassigned" : ticket.getAssignedTo().getFullName();
        ticket.assignTo(admin);
        ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket, admin, TicketEvent.ASSIGNED,
                previous + " → " + admin.getFullName()));
        return ticket;
    }

    @Transactional
    public TicketMessage sendReply(Ticket ticket, User admin, ReplyRequest request) {
        TicketMessage message = new TicketMessage(ticket, admin, request.body().trim(), false);
        messageRepository.save(message);
        ticket.markFirstResponse();
        ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket, admin, TicketEvent.REPLY_SENT,
                "Reply sent to " + ticket.getAuthor().getName()));
        return message;
    }

    @Transactional
    public TicketMessage addInternalNote(Ticket ticket, User admin, ReplyRequest request) {
        TicketMessage message = new TicketMessage(ticket, admin, request.body().trim(), true);
        messageRepository.save(message);
        ticket.touch();
        ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket, admin, TicketEvent.NOTE_ADDED,
                "Internal note added"));
        return message;
    }

    @Transactional
    public TicketMessage addAuthorReply(Ticket ticket, User authorUser, ReplyRequest request) {
        TicketMessage message = new TicketMessage(ticket, authorUser, request.body().trim(), false);
        messageRepository.save(message);
        ticket.touch();
        ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket, authorUser, TicketEvent.AUTHOR_REPLIED,
                "Author added a follow-up"));
        return message;
    }

    @Transactional
    public void recordEvent(Ticket ticket, User actor, String eventType, String detail) {
        eventRepository.save(new TicketEvent(ticket, actor, eventType, detail));
    }

    /**
     * Applies one action to many tickets in a single transaction.
     *
     * <p>Each ticket is only touched for the parts of the request that apply, and
     * an audit event is written for every change — a bulk action is still
     * traceable ticket by ticket. Tickets already in the target state are left
     * alone and excluded from the change counts.</p>
     */
    @Transactional
    public BulkResult bulkUpdate(List<UUID> ticketIds,
                                  TicketStatus status,
                                  TicketCategory category,
                                  TicketPriority priority,
                                  boolean assignToMe,
                                  String note,
                                  User actor) {
        List<Ticket> tickets = ticketRepository.findAllById(ticketIds);

        int statusChanged = 0;
        int categoryChanged = 0;
        int priorityChanged = 0;
        int assigned = 0;
        int noted = 0;
        List<UUID> changed = new ArrayList<>();

        for (Ticket ticket : tickets) {
            boolean touched = false;

            if (status != null && ticket.getStatus() != status) {
                TicketStatus previous = ticket.getStatus();
                ticket.changeStatus(status);
                eventRepository.save(new TicketEvent(ticket, actor, TicketEvent.STATUS_CHANGED,
                        previous.label() + " → " + status.label() + " (bulk action)"));
                statusChanged++;
                touched = true;
            }

            if (category != null && ticket.getCategory() != category) {
                TicketCategory previous = ticket.getCategory();
                ticket.overrideCategory(category);
                eventRepository.save(new TicketEvent(ticket, actor, TicketEvent.CATEGORY_OVERRIDDEN,
                        previous.label() + " → " + category.label() + " (bulk action)"));
                categoryChanged++;
                touched = true;
            }

            if (priority != null && ticket.getPriority() != priority) {
                TicketPriority previous = ticket.getPriority();
                ticket.overridePriority(priority);
                eventRepository.save(new TicketEvent(ticket, actor, TicketEvent.PRIORITY_OVERRIDDEN,
                        previous.label() + " → " + priority.label() + " (bulk action)"));
                priorityChanged++;
                touched = true;
            }

            if (assignToMe) {
                String previous = ticket.getAssignedTo() == null ? "Unassigned" : ticket.getAssignedTo().getFullName();
                ticket.assignTo(actor);
                eventRepository.save(new TicketEvent(ticket, actor, TicketEvent.ASSIGNED,
                        previous + " → " + actor.getFullName() + " (bulk action)"));
                assigned++;
                touched = true;
            }

            if (note != null && !note.isBlank()) {
                messageRepository.save(new TicketMessage(ticket, actor, note.strip(), true));
                eventRepository.save(new TicketEvent(ticket, actor, TicketEvent.NOTE_ADDED,
                        "Internal note added (bulk action)"));
                noted++;
                touched = true;
            }

            if (touched) {
                ticketRepository.save(ticket);
                changed.add(ticket.getId());
            }
        }

        return new BulkResult(statusChanged, categoryChanged, priorityChanged, assigned, noted, changed);
    }

    /**
     * The outcome of a bulk update.
     */
    public record BulkResult(int statusChanged,
                              int categoryChanged,
                              int priorityChanged,
                              int assigned,
                              int noted,
                              List<UUID> changedIds) {
    }
}
