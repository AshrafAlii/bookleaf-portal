package com.bookleaf.portal.service;

import com.bookleaf.portal.ai.AiGateway;
import com.bookleaf.portal.domain.AiUsageLog;
import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.domain.TicketStatus;
import com.bookleaf.portal.dto.AdminDtos.AdminDashboardResponse;
import com.bookleaf.portal.dto.AdminDtos.AiUsageEntry;
import com.bookleaf.portal.dto.AdminDtos.AiUsageSummary;
import com.bookleaf.portal.dto.AdminDtos.QueueStats;
import com.bookleaf.portal.dto.TicketDtos.TicketSummary;
import com.bookleaf.portal.repository.AiUsageLogRepository;
import com.bookleaf.portal.repository.AuthorRepository;
import com.bookleaf.portal.repository.BookRepository;
import com.bookleaf.portal.repository.TicketMessageRepository;
import com.bookleaf.portal.repository.TicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the numbers the BookLeaf ops team looks at first thing in the morning:
 * what is waiting, what is overdue, and what the AI has cost so far.
 */
@Service
public class AdminDashboardService {

    /** Tickets unanswered for longer than this are treated as breaching the 48-hour promise. */
    private static final long OVERDUE_HOURS = 48;

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiGateway aiGateway;
    private final TicketService ticketService;

    public AdminDashboardService(TicketRepository ticketRepository,
                                 TicketMessageRepository messageRepository,
                                 AuthorRepository authorRepository,
                                 BookRepository bookRepository,
                                 AiUsageLogRepository aiUsageLogRepository,
                                 AiGateway aiGateway,
                                 TicketService ticketService) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.aiGateway = aiGateway;
        this.ticketService = ticketService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        QueueStats queue = queueStats();
        AiUsageSummary ai = aiUsage();
        List<TicketSummary> urgent = urgentTickets();
        List<TicketSummary> recent = ticketService.adminQueue(
                        new com.bookleaf.portal.service.TicketQueryService.TicketStatusFilter(
                                null, null, null, null, null, null, null),
                        org.springframework.data.domain.PageRequest.of(0, 8,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Order.desc("priorityRank"),
                                        org.springframework.data.domain.Sort.Order.asc("createdAt"))))
                .getContent();

        return new AdminDashboardResponse(queue, ai, urgent, recent,
                authorRepository.count(), bookRepository.count());
    }

    @Transactional(readOnly = true)
    public QueueStats queueStats() {
        long open = ticketRepository.countByStatus(TicketStatus.OPEN);
        long inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long resolved = ticketRepository.countByStatus(TicketStatus.RESOLVED);
        long closed = ticketRepository.countByStatus(TicketStatus.CLOSED);

        List<Ticket> unresolved = ticketRepository.findAllForAdminStats();
        long awaitingFirstResponse = unresolved.stream()
                .filter(ticket -> ticket.getFirstResponseAt() == null)
                .count();
        long overdue = unresolved.stream()
                .filter(ticket -> Duration.between(ticket.getCreatedAt(), Instant.now()).toHours() > OVERDUE_HOURS)
                .count();
        long criticalOpen = unresolved.stream()
                .filter(ticket -> ticket.getPriority() == TicketPriority.CRITICAL)
                .count();
        double averageFirstResponseHours = unresolved.stream()
                .filter(ticket -> ticket.getFirstResponseAt() != null)
                .mapToDouble(ticket -> (double) Duration.between(ticket.getCreatedAt(),
                        ticket.getFirstResponseAt()).toMinutes() / 60.0)
                .average()
                .orElse(0.0);

        return new QueueStats(open, inProgress, resolved, closed, awaitingFirstResponse, overdue,
                criticalOpen, Math.round(averageFirstResponseHours * 10.0) / 10.0);
    }

    @Transactional(readOnly = true)
    public AiUsageSummary aiUsage() {
        long total = aiUsageLogRepository.countAll();
        long cached = aiUsageLogRepository.countCacheHits();
        long failed = aiUsageLogRepository.countFailures();
        long promptTokens = aiUsageLogRepository.sumPromptTokens();
        long completionTokens = aiUsageLogRepository.sumCompletionTokens();

        List<AiUsageLog> recent = aiUsageLogRepository.findRecent(50);
        double averageLatency = recent.stream()
                .filter(log -> log.getLatencyMs() != null)
                .mapToInt(AiUsageLog::getLatencyMs)
                .average()
                .orElse(0.0);

        return new AiUsageSummary(total, cached, failed, promptTokens, completionTokens,
                Math.round(averageLatency * 10.0) / 10.0,
                aiGateway.providerName(), aiGateway.modelName("DRAFT"));
    }

    @Transactional(readOnly = true)
    public List<AiUsageEntry> recentAiUsage(int limit) {
        return aiUsageLogRepository.findRecent(limit).stream()
                .map(log -> new AiUsageEntry(
                        log.getId(),
                        log.getPurpose(),
                        log.getProvider(),
                        log.getModel(),
                        log.getPromptTokens(),
                        log.getCompletionTokens(),
                        log.isCached(),
                        log.getLatencyMs(),
                        log.isSuccess(),
                        log.getErrorMessage(),
                        log.getCreatedAt().toString()))
                .toList();
    }

    /** The "needs attention now" strip: critical and high priority, oldest first. */
    @Transactional(readOnly = true)
    public List<TicketSummary> urgentTickets() {
        return ticketRepository.findUrgent(
                        org.springframework.data.domain.PageRequest.of(0, 6,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Order.desc("priorityRank"),
                                        org.springframework.data.domain.Sort.Order.asc("createdAt"))))
                .map(ticket -> com.bookleaf.portal.dto.TicketDtos.toSummary(ticket,
                        messageRepository.countByTicketId(ticket.getId()), null))
                .toList();
    }
}
