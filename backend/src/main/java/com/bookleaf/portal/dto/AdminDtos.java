package com.bookleaf.portal.dto;

import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.domain.TicketStatus;
import java.util.List;

/**
 * Admin dashboard, AI usage and reference-data payloads.
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
}
