package com.bookleaf.portal.service;

import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketEvent;
import com.bookleaf.portal.domain.TicketMessage;
import com.bookleaf.portal.domain.User;
import com.bookleaf.portal.dto.TicketDtos.TicketEventResponse;
import com.bookleaf.portal.dto.TicketDtos.TicketMessageResponse;
import com.bookleaf.portal.dto.TicketDtos.TicketSummary;
import org.springframework.stereotype.Component;

/**
 * Entity → DTO mapping for tickets.
 *
 * <p>Kept separate from the services so that mapping rules live in one place and
 * so that entities are only ever touched inside an open transaction (the
 * services that call this are transactional).</p>
 */
@Component
public class TicketMapper {

    public TicketSummary toSummary(Ticket ticket, long messageCount, String lastMessagePreview) {
        long ageHours = java.time.Duration.between(ticket.getCreatedAt(), java.time.Instant.now()).toHours();
        boolean awaitingAdmin = !ticket.getStatus().isTerminal()
                && (ticket.getFirstResponseAt() == null
                || ticket.getUpdatedAt().isAfter(ticket.getFirstResponseAt()));
        return new TicketSummary(
                ticket.getId(),
                ticket.getTicketCode(),
                ticket.getSubject(),
                ticket.getStatus(),
                ticket.getStatus().label(),
                ticket.getCategory(),
                ticket.getCategory().label(),
                ticket.getCategorySource(),
                ticket.getPriority(),
                ticket.getPriority().label(),
                ticket.getPrioritySource(),
                ticket.getBook() == null ? null : ticket.getBook().getId(),
                ticket.getBook() == null ? null : ticket.getBook().getTitle(),
                ticket.getBook() == null ? null : ticket.getBook().getBookCode(),
                ticket.getAuthor().getId(),
                ticket.getAuthor().getName(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getFirstResponseAt(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getFullName(),
                messageCount,
                awaitingAdmin,
                ageHours,
                lastMessagePreview);
    }

    public TicketMessageResponse toMessageResponse(TicketMessage message) {
        User author = message.getAuthorUser();
        return new TicketMessageResponse(
                message.getId(),
                message.getBody(),
                message.isInternal(),
                author == null ? "BookLeaf Support" : author.getFullName(),
                author == null ? "ADMIN" : author.getRole().name(),
                message.getCreatedAt());
    }

    public TicketEventResponse toEventResponse(TicketEvent event) {
        User actor = event.getActorUser();
        return new TicketEventResponse(
                event.getId(),
                event.getEventType(),
                event.getDetail(),
                actor == null ? "System" : actor.getFullName(),
                event.getCreatedAt());
    }

    public String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String single = text.replaceAll("\\s+", " ").strip();
        return single.length() <= max ? single : single.substring(0, max) + "…";
    }
}
