package com.bookleaf.portal.web;

import com.bookleaf.portal.dto.BookDtos.AuthorDashboardResponse;
import com.bookleaf.portal.dto.BookDtos.BookResponse;
import com.bookleaf.portal.dto.TicketDtos.AuthorTicketDetailResponse;
import com.bookleaf.portal.dto.TicketDtos.CreateTicketRequest;
import com.bookleaf.portal.dto.TicketDtos.ReplyRequest;
import com.bookleaf.portal.dto.TicketDtos.TicketSummary;
import com.bookleaf.portal.exception.ApiException;
import com.bookleaf.portal.security.AppUserPrincipal;
import com.bookleaf.portal.service.AuthorService;
import com.bookleaf.portal.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The author-facing API. Every endpoint is scoped to the signed-in author:
 * an author can only ever reach their own books and tickets.
 */
@RestController
@RequestMapping("/api/v1/author")
@Tag(name = "Author portal", description = "Books, dashboards and support tickets for the signed-in author")
@SecurityRequirement(name = "bearerAuth")
public class AuthorController {

    private final AuthorService authorService;
    private final TicketService ticketService;

    public AuthorController(AuthorService authorService, TicketService ticketService) {
        this.authorService = authorService;
        this.ticketService = ticketService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Author dashboard with royalty totals and recent tickets")
    public AuthorDashboardResponse dashboard(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authorService.dashboard(requireAuthorId(principal));
    }

    @GetMapping("/books")
    @Operation(summary = "List the author's books with royalty figures")
    public List<BookResponse> books(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authorService.books(requireAuthorId(principal));
    }

    @GetMapping("/books/{bookId}")
    @Operation(summary = "Fetch one book belonging to the author")
    @ApiResponse(responseCode = "404", description = "Book not found for this author")
    public BookResponse book(@AuthenticationPrincipal AppUserPrincipal principal,
                              @PathVariable UUID bookId) {
        return authorService.book(requireAuthorId(principal), bookId);
    }

    @GetMapping("/tickets")
    @Operation(summary = "List the author's support tickets, newest first")
    public List<TicketSummary> tickets(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ticketService.listAuthorTickets(requireAuthorId(principal));
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(summary = "Fetch a ticket thread (internal notes are never included)")
    @ApiResponse(responseCode = "404", description = "Ticket not found for this author")
    public AuthorTicketDetailResponse ticket(@AuthenticationPrincipal AppUserPrincipal principal,
                                              @PathVariable UUID ticketId) {
        return ticketService.getAuthorTicket(requireAuthorId(principal), ticketId);
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise a support ticket",
            description = "The ticket is automatically classified and prioritised by the AI triage model. "
                    + "If the AI provider is unavailable the built-in fallback rules are used instead.")
    @ApiResponse(responseCode = "201", description = "Ticket created")
    @ApiResponse(responseCode = "400", description = "Validation failed or the book does not belong to the author")
    public TicketSummary createTicket(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(principal, request);
    }

    @PostMapping("/tickets/{ticketId}/replies")
    @Operation(summary = "Add a follow-up message to an existing ticket")
    public TicketSummary reply(@AuthenticationPrincipal AppUserPrincipal principal,
                               @PathVariable UUID ticketId,
                               @Valid @RequestBody ReplyRequest request) {
        return ticketService.addAuthorReply(principal, ticketId, request);
    }

    private UUID requireAuthorId(AppUserPrincipal principal) {
        if (principal == null || principal.getAuthorId() == null) {
            throw ApiException.forbidden("This endpoint is only available to author accounts");
        }
        return principal.getAuthorId();
    }
}
