package com.bookleaf.portal.web;

import com.bookleaf.portal.security.AppUserPrincipal;
import com.bookleaf.portal.service.TicketEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The real-time ticket event stream.
 *
 * <p>Admins receive every ticket event; authors receive only events for their
 * own tickets. The authorisation check happens server side in
 * {@link TicketEventPublisher}, so an author cannot receive another author's
 * updates even by tampering with the client.</p>
 */
@RestController
@RequestMapping("/api/v1/stream")
@Tag(name = "Real-time", description = "Server-Sent Events stream of ticket updates")
@SecurityRequirement(name = "bearerAuth")
public class StreamController {

    private final TicketEventPublisher eventPublisher;

    public StreamController(TicketEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @GetMapping(path = "/tickets", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to ticket updates",
            description = "Returns a Server-Sent Events stream. Admins receive every ticket event, "
                    + "authors only receive events for their own tickets.")
    public SseEmitter stream(@AuthenticationPrincipal AppUserPrincipal principal) {
        boolean admin = principal != null && principal.isAdmin();
        return eventPublisher.subscribe(
                principal == null ? null : principal.getUserId(),
                principal == null ? null : principal.getAuthorId(),
                admin);
    }
}
