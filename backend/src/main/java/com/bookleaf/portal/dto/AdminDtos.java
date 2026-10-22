package com.bookleaf.portal.dto;

import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.domain.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bulk-update and saved-filter payloads.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record QueueStats(long open,
                             long inProgress,
                             long resolved,
                             long closed,
                             long awaitingFirstResponse,
                             long overdue,
                             long criticalOpen,
                             double averageFirstResponseHours) {
    }

    public record AiUsageSummary(long totalCalls,
                                 long cachedCalls,
                                 long failedCalls,
                                 long promptTokens,
                                 long completionTokens,
                                 double averageLatencyMs,
                                 String provider,
                                 String model) {
    }

    public record AiUsageEntry(long id,
                                String purpose,
                                String provider,
                                String model,
                                Integer promptTokens,
                                Integer completionTokens,
                                boolean cached,
                                Integer latencyMs,
                                boolean success,
                                String errorMessage,
                                String createdAt) {
    }

    public record AdminDashboardResponse(QueueStats queue,
                                         AiUsageSummary ai,
                                         List<TicketDtos.TicketSummary> urgentTickets,
                                         List<TicketDtos.TicketSummary> recentTickets,
                                         long totalAuthors,
                                         long totalBooks) {
    }

    /**
     * Reference data and AI status. Publicly readable so the login page can show
     * whether AI is configured without authenticating.
     */
    public record MetaResponse(List<String> categories,
                               List<String> priorities,
                               List<String> statuses,
                               boolean aiEnabled,
                               String aiProvider,
                               String aiModel) {
    }

    public static List<String> categoryLabels() {
        return java.util.Arrays.stream(TicketCategory.values()).map(TicketCategory::label).toList();
    }

    public static List<String> priorityLabels() {
        return java.util.Arrays.stream(TicketPriority.values()).map(TicketPriority::label).toList();
    }

    public static List<String> statusLabels() {
        return java.util.Arrays.stream(TicketStatus.values()).map(TicketStatus::label).toList();
    }

    /**
     * Applies one action to many tickets at once.
     *
     * <p>Every field is optional — only the ones that are set are applied, so the
     * same endpoint covers "assign these 30 to me" and "close these 12 as
     * resolved".</p>
     *
     * @param ticketIds   the tickets to act on
     * @param status      optional new status
     * @param category    optional category override
     * @param priority    optional priority override
     * @param assignToMe  assign the tickets to the signed-in admin
     * @param note        optional internal note added to every ticket
     */
    public record BulkUpdateRequest(
            @NotEmpty(message = "Select at least one ticket")
            @Size(max = 200, message = "Select at most 200 tickets at a time")
            List<UUID> ticketIds,

            TicketStatus status,
            TicketCategory category,
            TicketPriority priority,
            Boolean assignToMe,

            @Size(max = 2000, message = "The note must be at most 2000 characters")
            String note) {
    }

    /**
     * @param updated      how many tickets were actually changed
     * @param statusChanged / categoryChanged / priorityChanged / assigned / noted
     *                      how many were affected by each part of the request
     * @param ticketIds    the ids of the tickets that were changed
     */
    public record BulkUpdateResponse(int requested,
                                     int updated,
                                     int statusChanged,
                                     int categoryChanged,
                                     int priorityChanged,
                                     int assigned,
                                     int noted,
                                     List<UUID> ticketIds) {
    }

    /**
     * @param filters the queue filters to store, as the same shape the queue
     *                endpoint accepts
     */
    public record SavedFilterRequest(
            @NotBlank(message = "Give the filter a name")
            @Size(min = 2, max = 120, message = "The name must be between 2 and 120 characters")
            String name,

            @NotBlank(message = "The filter definition is required")
            String filters) {
    }

    public record SavedFilterResponse(UUID id,
                                      String name,
                                      String filters,
                                      Instant createdAt) {
    }
}
