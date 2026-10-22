package com.bookleaf.portal.service;

import com.bookleaf.portal.ai.AiDraftService;
import com.bookleaf.portal.ai.AiTriageService;
import com.bookleaf.portal.ai.AuthorContextBuilder;
import com.bookleaf.portal.ai.DraftResult;
import com.bookleaf.portal.ai.TriageResult;
import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.Book;
import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketEvent;
import com.bookleaf.portal.domain.User;
import com.bookleaf.portal.dto.TicketDtos.AuthorTicketDetailResponse;
import com.bookleaf.portal.dto.TicketDtos.CreateTicketRequest;
import com.bookleaf.portal.dto.TicketDtos.OverrideCategoryRequest;
import com.bookleaf.portal.dto.TicketDtos.OverridePriorityRequest;
import com.bookleaf.portal.dto.TicketDtos.ReplyRequest;
import com.bookleaf.portal.dto.TicketDtos.TicketDetailResponse;
import com.bookleaf.portal.dto.TicketDtos.TicketSummary;
import com.bookleaf.portal.dto.TicketDtos.UpdateStatusRequest;
import com.bookleaf.portal.exception.ApiException;
import com.bookleaf.portal.repository.AuthorRepository;
import com.bookleaf.portal.repository.BookRepository;
import com.bookleaf.portal.repository.UserRepository;
import com.bookleaf.portal.security.AppUserPrincipal;
import com.bookleaf.portal.service.TicketQueryService.TicketStatusFilter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the ticket lifecycle.
 *
 * <p>This layer deliberately does <em>not</em> open a transaction: the AI calls
 * are slow network round trips and must not hold database locks open. Writes go
 * through {@link TicketCommandService} (which owns its transactions) and reads
 * through {@link TicketQueryService} (which owns its own).</p>
 */
@Service
public class TicketService {

    private final TicketCommandService commandService;
    private final TicketQueryService queryService;
    private final TicketEventPublisher eventPublisher;
    private final AiTriageService triageService;
    private final AiDraftService draftService;
    private final AuthorContextBuilder authorContextBuilder;
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public TicketService(TicketCommandService commandService,
                         TicketQueryService queryService,
                         TicketEventPublisher eventPublisher,
                         AiTriageService triageService,
                         AiDraftService draftService,
                         AuthorContextBuilder authorContextBuilder,
                         AuthorRepository authorRepository,
                         BookRepository bookRepository,
                         UserRepository userRepository) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.eventPublisher = eventPublisher;
        this.triageService = triageService;
        this.draftService = draftService;
        this.authorContextBuilder = authorContextBuilder;
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------ author side

    /**
     * Creates a ticket, classifying and scoring it automatically.
     *
     * <p>The triage call happens before the write so the ticket is never left in an
     * unclassified state, and a provider failure falls back to deterministic rules
     * rather than blocking the author.</p>
     */
    public TicketSummary createTicket(AppUserPrincipal principal, CreateTicketRequest request) {
        UUID authorId = principal.getAuthorId();
        if (authorId == null) {
            throw ApiException.forbidden("Only author accounts can raise support tickets");
        }

        Author author = authorRepository.findById(authorId)
                .orElseThrow(() -> ApiException.notFound("Author profile not found"));
        List<Book> books = bookRepository.findAllForAuthor(authorId);
        String authorContext = authorContextBuilder.build(author, books);
        String bookLabel = resolveBookLabel(request.bookId(), books);

        TriageResult triage = triageService.triage(request.subject(), request.description(), bookLabel,
                authorContext);

        Ticket ticket = commandService.persistNewTicket(authorId, request, triage.category(), triage.priority(),
                triage.rationale(), triage.source());

        if (triage.note() != null) {
            commandService.recordEvent(ticket, author.getUser(), TicketEvent.AI_CLASSIFIED, triage.note());
        }

        eventPublisher.publish(ticket, "TICKET_CREATED",
                "New " + triage.priority().label().toLowerCase() + "-priority ticket from " + author.getName());

        return queryService.summaryFor(ticket.getId());
    }

    private String resolveBookLabel(UUID bookId, List<Book> books) {
        if (bookId == null) {
            return "General / Account Level";
        }
        return books.stream()
                .filter(book -> book.getId().equals(bookId))
                .map(book -> book.getTitle() + " (" + book.getBookCode() + ")")
                .findFirst()
                .orElse("General / Account Level");
    }

    public List<TicketSummary> listAuthorTickets(UUID authorId) {
        return queryService.listAuthorTickets(authorId);
    }

    public AuthorTicketDetailResponse getAuthorTicket(UUID authorId, UUID ticketId) {
        return queryService.getAuthorTicket(authorId, ticketId);
    }

    public TicketSummary addAuthorReply(AppUserPrincipal principal, UUID ticketId, ReplyRequest request) {
        UUID authorId = principal.getAuthorId();
        Ticket ticket = commandService.loadForAuthor(ticketId, authorId);
        User authorUser = resolveActor(principal);

        commandService.addAuthorReply(ticket, authorUser, request);
        eventPublisher.publish(ticket, "AUTHOR_REPLIED",
                authorUser.getFullName() + " replied to " + ticket.getTicketCode());
        return queryService.summaryFor(ticketId);
    }

    // ------------------------------------------------------------------ admin side

    public Page<TicketSummary> adminQueue(TicketStatusFilter filter, Pageable pageable) {
        return queryService.adminQueue(filter, pageable);
    }

    public TicketDetailResponse getAdminTicket(UUID ticketId) {
        return queryService.getAdminTicket(ticketId);
    }

    public TicketDetailResponse updateStatus(UUID ticketId, UpdateStatusRequest request, AppUserPrincipal actor) {
        Ticket ticket = commandService.loadForAdmin(ticketId);
        User admin = resolveActor(actor);
        Ticket updated = commandService.changeStatus(ticket, request.status(), admin);
        eventPublisher.publish(updated, "STATUS_CHANGED",
                updated.getTicketCode() + " is now " + request.status().label());
        return queryService.getAdminTicket(ticketId);
    }

    public TicketDetailResponse overrideCategory(UUID ticketId, OverrideCategoryRequest request,
                                                  AppUserPrincipal actor) {
        Ticket ticket = commandService.loadForAdmin(ticketId);
        User admin = resolveActor(actor);
        Ticket updated = commandService.overrideCategory(ticket, request.category(), admin);
        eventPublisher.publish(updated, "CATEGORY_OVERRIDDEN",
                updated.getTicketCode() + " category set to " + request.category().label());
        return queryService.getAdminTicket(ticketId);
    }

    public TicketDetailResponse overridePriority(UUID ticketId, OverridePriorityRequest request,
                                                  AppUserPrincipal actor) {
        Ticket ticket = commandService.loadForAdmin(ticketId);
        User admin = resolveActor(actor);
        Ticket updated = commandService.overridePriority(ticket, request.priority(), admin);
        eventPublisher.publish(updated, "PRIORITY_OVERRIDDEN",
                updated.getTicketCode() + " priority set to " + request.priority().label());
        return queryService.getAdminTicket(ticketId);
    }

    public TicketDetailResponse assignToMe(UUID ticketId, AppUserPrincipal actor) {
        Ticket ticket = commandService.loadForAdmin(ticketId);
        User admin = resolveActor(actor);
        Ticket updated = commandService.assignTo(ticket, admin);
        eventPublisher.publish(updated, "ASSIGNED",
                updated.getTicketCode() + " assigned to " + admin.getFullName());
        return queryService.getAdminTicket(ticketId);
    }

    public TicketDetailResponse sendReply(UUID ticketId, ReplyRequest request, AppUserPrincipal actor) {
        Ticket ticket = commandService.loadForAdmin(ticketId);
        User admin = resolveActor(actor);
        commandService.sendReply(ticket, admin, request);
        eventPublisher.publish(ticket, "REPLY_SENT", "BookLeaf replied to " + ticket.getTicketCode());
        return queryService.getAdminTicket(ticketId);
    }

    public TicketDetailResponse addInternalNote(UUID ticketId, ReplyRequest request, AppUserPrincipal actor) {
        Ticket ticket = commandService.loadForAdmin(ticketId);
        User admin = resolveActor(actor);
        commandService.addInternalNote(ticket, admin, request);
        eventPublisher.publish(ticket, "NOTE_ADDED", "Internal note added to " + ticket.getTicketCode());
        return queryService.getAdminTicket(ticketId);
    }

    /**
     * Generates an AI draft for the admin to edit.
     *
     * <p>Never throws: if the provider is unavailable the admin simply gets a
     * message explaining that and writes the reply themselves.</p>
     */
    public DraftResult generateDraft(UUID ticketId) {
        Ticket ticket = commandService.loadForAdmin(ticketId);
        Author author = ticket.getAuthor();
        List<Book> books = bookRepository.findAllForAuthor(author.getId());
        String authorContext = authorContextBuilder.build(author, books);
        return draftService.generate(ticket, authorContext);
    }

    // ------------------------------------------------------------------ helpers

    private User resolveActor(AppUserPrincipal principal) {
        return userRepository.findById(principal.getUserId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
