package com.bookleaf.portal.dto;

import com.bookleaf.portal.domain.DecisionSource;
import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.domain.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ticket payloads for both portals.
 */
public final class TicketDtos {

    private TicketDtos() {
    }

    // ------------------------------------------------------------------ requests

    public record CreateTicketRequest(
            UUID bookId,

            @NotBlank(message = "Subject is required")
            @Size(min = 5, max = 200, message = "Subject must be between 5 and 200 characters")
            String subject,

            @NotBlank(message = "Description is required")
            @Size(min = 20, max = 8000, message = "Description must be between 20 and 8000 characters")
            String description,

            @Size(max = 255, message = "Attachment name is too long")
            String attachmentName) {
    }

    public record UpdateStatusRequest(
            @NotNull(message = "Status is required")
            TicketStatus status) {
    }

    public record OverrideCategoryRequest(
            @NotNull(message = "Category is required")
            TicketCategory category) {
    }

    public record OverridePriorityRequest(
            @NotNull(message = "Priority is required")
            TicketPriority priority) {
    }

    public record ReplyRequest(
            @NotBlank(message = "A reply body is required")
            @Size(min = 2, max = 8000, message = "Reply must be between 2 and 8000 characters")
            String body) {
    }

    public record NoteRequest(
            @NotBlank(message = "A note body is required")
            @Size(min = 2, max = 8000, message = "Note must be between 2 and 8000 characters")
            String body) {
    }

    // ------------------------------------------------------------------ responses

    /**
     * @param categorySource  {@code AI}, {@code HEURISTIC} or {@code MANUAL} — lets the admin see
     *                        when a human overrode the model
     * @param awaitingAdmin   true when the last word on the ticket belongs to BookLeaf
     */
    public record TicketSummary(UUID id,
                                String ticketCode,
                                String subject,
                                TicketStatus status,
                                String statusLabel,
                                TicketCategory category,
                                String categoryLabel,
                                DecisionSource categorySource,
                                TicketPriority priority,
                                String priorityLabel,
                                DecisionSource prioritySource,
                                UUID bookId,
                                String bookTitle,
                                String bookCode,
                                UUID authorId,
                                String authorName,
                                Instant createdAt,
                                Instant updatedAt,
                                Instant firstResponseAt,
                                String assignedToName,
                                long messageCount,
                                boolean awaitingAdmin,
                                long ageHours,
                                String lastMessagePreview) {
    }

    public record TicketMessageResponse(Long id,
                                        String body,
                                        boolean internal,
                                        String authorName,
                                        String authorRole,
                                        Instant createdAt) {
    }

    public record TicketEventResponse(Long id,
                                      String eventType,
                                      String detail,
                                      String actorName,
                                      Instant createdAt) {
    }

    public record AuthorSummary(UUID id, String authorCode, String name, String email, String city) {
    }

    /**
     * Full ticket view for the admin, including the live account context that the
     * AI uses, so the admin can see the same numbers the model saw.
     */
    public record TicketDetailResponse(TicketSummary ticket,
                                       String description,
                                       String attachmentName,
                                       AuthorSummary author,
                                       BookDtos.BookResponse book,
                                       List<TicketMessageResponse> messages,
                                       List<TicketEventResponse> events,
                                       String priorityRationale,
                                       String aiNote) {
    }

    /**
     * Full ticket view for the author. Internal notes are never included.
     */
    public record AuthorTicketDetailResponse(TicketSummary ticket,
                                              String description,
                                              String attachmentName,
                                              BookDtos.BookResponse book,
                                              List<TicketMessageResponse> messages,
                                              List<TicketEventResponse> events) {
    }

    public record PageResponse<T>(List<T> content,
                                  int page,
                                  int size,
                                  long totalElements,
                                  int totalPages,
                                  boolean first,
                                  boolean last) {
    }

    // ------------------------------------------------------------------ helpers

    public static TicketSummary toSummary(Ticket ticket, long messageCount, String lastMessagePreview) {
        long ageHours = java.time.Duration.between(ticket.getCreatedAt(), Instant.now()).toHours();
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
}
