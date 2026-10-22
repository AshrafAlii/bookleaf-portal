package com.bookleaf.portal.service;

import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketMessage;
import com.bookleaf.portal.dto.BookDtos;
import com.bookleaf.portal.dto.TicketDtos;
import com.bookleaf.portal.dto.TicketDtos.AuthorTicketDetailResponse;
import com.bookleaf.portal.dto.TicketDtos.AuthorSummary;
import com.bookleaf.portal.dto.TicketDtos.TicketDetailResponse;
import com.bookleaf.portal.dto.TicketDtos.TicketSummary;
import com.bookleaf.portal.exception.ApiException;
import com.bookleaf.portal.repository.TicketEventRepository;
import com.bookleaf.portal.repository.TicketMessageRepository;
import com.bookleaf.portal.repository.TicketRepository;
import com.bookleaf.portal.repository.TicketSpecifications;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All ticket reads.
 *
 * <p>Kept in its own bean so the transaction boundary is unambiguous: every
 * method here runs inside a transaction, which matters because the ticket
 * aggregates lazy associations (author, book, assignee, platform list) that are
 * touched while mapping to DTOs.</p>
 */
@Service
public class TicketQueryService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketEventRepository eventRepository;
    private final TicketMapper mapper;

    public TicketQueryService(TicketRepository ticketRepository,
                              TicketMessageRepository messageRepository,
                              TicketEventRepository eventRepository,
                              TicketMapper mapper) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> listAuthorTickets(UUID authorId) {
        List<Ticket> tickets = ticketRepository.findAllForAuthor(authorId);
        Map<UUID, List<TicketMessage>> messages = loadMessages(tickets.stream().map(Ticket::getId).toList());
        return tickets.stream()
                .map(ticket -> summarise(ticket, messages.getOrDefault(ticket.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketSummary summaryFor(UUID ticketId) {
        Ticket ticket = loadTicket(ticketId);
        List<TicketMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return summarise(ticket, messages);
    }

    @Transactional(readOnly = true)
    public AuthorTicketDetailResponse getAuthorTicket(UUID authorId, UUID ticketId) {
        Ticket ticket = loadTicket(ticketId);
        if (!ticket.getAuthor().getId().equals(authorId)) {
            // Deliberately 404 rather than 403: an author must not be able to probe
            // for the existence of other authors' tickets.
            throw ApiException.notFound("Ticket not found");
        }
        List<TicketMessage> visible = messageRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticketId);
        return new AuthorTicketDetailResponse(
                summarise(ticket, visible),
                ticket.getDescription(),
                ticket.getAttachmentName(),
                ticket.getBook() == null ? null : BookDtos.BookResponse.from(ticket.getBook()),
                visible.stream().map(mapper::toMessageResponse).toList(),
                eventRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                        .map(mapper::toEventResponse).toList());
    }

    @Transactional(readOnly = true)
    public Page<TicketSummary> adminQueue(TicketStatusFilter filter, Pageable pageable) {
        Specification<Ticket> spec = Specification
                .where(TicketSpecifications.hasStatus(filter.status()))
                .and(TicketSpecifications.hasCategory(filter.category()))
                .and(TicketSpecifications.hasPriority(filter.priority()))
                .and(TicketSpecifications.assignedTo(filter.assignedTo()))
                .and(TicketSpecifications.createdBetween(filter.from(), filter.to()))
                .and(TicketSpecifications.matchesText(filter.search()));

        Page<Ticket> tickets = ticketRepository.findAll(spec, pageable);
        Map<UUID, List<TicketMessage>> messages = loadMessages(tickets.getContent().stream()
                .map(Ticket::getId).toList());
        return tickets.map(ticket -> summarise(ticket, messages.getOrDefault(ticket.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getAdminTicket(UUID ticketId) {
        Ticket ticket = loadTicket(ticketId);
        List<TicketMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        Author author = ticket.getAuthor();
        return new TicketDetailResponse(
                summarise(ticket, messages),
                ticket.getDescription(),
                ticket.getAttachmentName(),
                new AuthorSummary(author.getId(), author.getAuthorCode(), author.getName(),
                        author.getEmail(), author.getCity()),
                ticket.getBook() == null ? null : BookDtos.BookResponse.from(ticket.getBook()),
                messages.stream().map(mapper::toMessageResponse).toList(),
                eventRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                        .map(mapper::toEventResponse).toList(),
                ticket.getPriorityRationale(),
                null);
    }

    private Ticket loadTicket(UUID ticketId) {
        return ticketRepository.findDetailedById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Ticket not found"));
    }

    private Map<UUID, List<TicketMessage>> loadMessages(List<UUID> ticketIds) {
        Map<UUID, List<TicketMessage>> map = new LinkedHashMap<>();
        if (ticketIds.isEmpty()) {
            return map;
        }
        for (TicketMessage message : messageRepository.findAllForTickets(ticketIds)) {
            map.computeIfAbsent(message.getTicket().getId(), key -> new ArrayList<>()).add(message);
        }
        return map;
    }

    private TicketSummary summarise(Ticket ticket, List<TicketMessage> messages) {
        String preview = null;
        if (!messages.isEmpty()) {
            preview = mapper.truncate(messages.get(messages.size() - 1).getBody(), 140);
        }
        return mapper.toSummary(ticket, messages.size(), preview);
    }

    /**
     * The admin queue filters, grouped so the controller can pass them straight
     * through without a long parameter list.
     */
    public record TicketStatusFilter(com.bookleaf.portal.domain.TicketStatus status,
                                     com.bookleaf.portal.domain.TicketCategory category,
                                     com.bookleaf.portal.domain.TicketPriority priority,
                                     UUID assignedTo,
                                     Instant from,
                                     Instant to,
                                     String search) {
    }
}
