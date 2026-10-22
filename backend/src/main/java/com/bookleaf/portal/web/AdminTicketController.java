package com.bookleaf.portal.web;

import com.bookleaf.portal.ai.DraftResult;
import com.bookleaf.portal.dto.TicketDtos.OverrideCategoryRequest;
import com.bookleaf.portal.dto.TicketDtos.OverridePriorityRequest;
import com.bookleaf.portal.dto.TicketDtos.ReplyRequest;
import com.bookleaf.portal.dto.TicketDtos.TicketDetailResponse;
import com.bookleaf.portal.dto.TicketDtos.UpdateStatusRequest;
import com.bookleaf.portal.security.AppUserPrincipal;
import com.bookleaf.portal.service.TicketQueryService;
import com.bookleaf.portal.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.bookleaf.portal.dto.TicketDtos.TicketSummary;

/**
 * The admin-facing ticket API. Access is restricted to {@code ROLE_ADMIN} by the
 * security configuration; the {@code /api/v1/admin/**} prefix is guarded there.
 */
@RestController
@RequestMapping("/api/v1/admin/tickets")
@Tag(name = "Admin tickets", description = "Manage the support queue, overrides and AI-drafted replies")
@SecurityRequirement(name = "bearerAuth")
public class AdminTicketController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TicketService ticketService;

    public AdminTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "Search the support queue",
            description = "Filter by status, category, priority, assignee and date; sort by urgency or age.")
    public com.bookleaf.portal.dto.TicketDtos.PageResponse<com.bookleaf.portal.dto.TicketDtos.TicketSummary> queue(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "priorityRank,desc") String sort) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, parseSort(sort));
        Page<TicketSummary> ticketPage = ticketService.adminQueue(
                new TicketQueryService.TicketStatusFilter(
                        parseEnum(status, com.bookleaf.portal.domain.TicketStatus.class),
                        parseEnum(category, com.bookleaf.portal.domain.TicketCategory.class),
                        parseEnum(priority, com.bookleaf.portal.domain.TicketPriority.class),
                        assignedTo, from, to, search),
                pageable);
        // Serialised as an explicit DTO rather than Spring's PageImpl so the JSON
        // shape is part of the API contract and cannot drift.
        return new com.bookleaf.portal.dto.TicketDtos.PageResponse<>(
                ticketPage.getContent(),
                ticketPage.getNumber(),
                ticketPage.getSize(),
                ticketPage.getTotalElements(),
                ticketPage.getTotalPages(),
                ticketPage.isFirst(),
                ticketPage.isLast());
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Full ticket view including internal notes and the audit trail")
    public TicketDetailResponse ticket(@PathVariable UUID ticketId) {
        return ticketService.getAdminTicket(ticketId);
    }

    @PutMapping("/{ticketId}/status")
    @Operation(summary = "Change the ticket status")
    public TicketDetailResponse updateStatus(@PathVariable UUID ticketId,
                                             @Valid @RequestBody UpdateStatusRequest request,
                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        return ticketService.updateStatus(ticketId, request, principal);
    }

    @PutMapping("/{ticketId}/category")
    @Operation(summary = "Override the AI classification")
    @ApiResponse(responseCode = "200", description = "Category overridden")
    public TicketDetailResponse overrideCategory(@PathVariable UUID ticketId,
                                                 @Valid @RequestBody OverrideCategoryRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return ticketService.overrideCategory(ticketId, request, principal);
    }

    @PutMapping("/{ticketId}/priority")
    @Operation(summary = "Override the AI priority score")
    public TicketDetailResponse overridePriority(@PathVariable UUID ticketId,
                                                 @Valid @RequestBody OverridePriorityRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return ticketService.overridePriority(ticketId, request, principal);
    }

    @PostMapping("/{ticketId}/assign")
    @Operation(summary = "Assign the ticket to the signed-in admin")
    public TicketDetailResponse assignToMe(@PathVariable UUID ticketId,
                                            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ticketService.assignToMe(ticketId, principal);
    }

    @PostMapping("/{ticketId}/replies")
    @Operation(summary = "Send a reply to the author")
    public TicketDetailResponse reply(@PathVariable UUID ticketId,
                                      @Valid @RequestBody ReplyRequest request,
                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        return ticketService.sendReply(ticketId, request, principal);
    }

    @PostMapping("/{ticketId}/notes")
    @Operation(summary = "Add an internal note (never visible to the author)")
    public TicketDetailResponse note(@PathVariable UUID ticketId,
                                     @Valid @RequestBody ReplyRequest request,
                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        return ticketService.addInternalNote(ticketId, request, principal);
    }

    @PostMapping("/{ticketId}/draft")
    @Operation(summary = "Generate an AI draft reply for this ticket",
            description = "The draft is grounded in the BookLeaf knowledge base and the author's live "
                    + "account data. Returns a degraded response instead of an error when the AI provider "
                    + "is unavailable, so the admin can write the reply manually.")
    public DraftResult draft(@PathVariable UUID ticketId) {
        return ticketService.generateDraft(ticketId);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("priorityRank"), Sort.Order.asc("createdAt"));
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Order order = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Order.asc(property)
                : Sort.Order.desc(property);
        // Age is expressed by createdAt; urgency by the numeric priority mirror.
        if ("age".equals(property)) {
            return Sort.by(Sort.Order.asc("createdAt"));
        }
        if ("priority".equals(property)) {
            return Sort.by(Sort.Order.desc("priorityRank"));
        }
        return Sort.by(order);
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalised = value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return Enum.valueOf(type, normalised);
        } catch (IllegalArgumentException ex) {
            throw com.bookleaf.portal.exception.ApiException.badRequest(
                    "Unknown " + type.getSimpleName().toLowerCase() + " value: " + value);
        }
    }
}
