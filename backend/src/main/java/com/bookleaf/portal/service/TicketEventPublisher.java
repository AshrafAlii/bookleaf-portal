package com.bookleaf.portal.service;

import com.bookleaf.portal.domain.Ticket;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Real-time ticket updates over Server-Sent Events.
 *
 * <p>SSE was chosen over WebSockets because the traffic is one-directional
 * (server → client), it rides on plain HTTP so it works behind any proxy or
 * load balancer, and it reconnects automatically in the browser.</p>
 *
 * <p>Each subscriber is either an admin (who receives every ticket event) or an
 * author (who only receives events for their own tickets). The authorisation
 * decision is made here, at the point of publication, so an author can never
 * receive another author's ticket through the stream.</p>
 */
@Service
public class TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();

    private record Subscription(UUID id, SseEmitter emitter, UUID authorId, boolean admin) {
    }

    /**
     * Payload delivered to the browser. Kept small — the client refetches the
     * affected ticket rather than receiving the whole record over the stream.
     */
    public record TicketStreamEvent(String type,
                                    UUID ticketId,
                                    String ticketCode,
                                    String status,
                                    String priority,
                                    String category,
                                    String message,
                                    String at) {

        static TicketStreamEvent from(Ticket ticket, String type, String message) {
            return new TicketStreamEvent(
                    type,
                    ticket.getId(),
                    ticket.getTicketCode(),
                    ticket.getStatus().name(),
                    ticket.getPriority().name(),
                    ticket.getCategory().name(),
                    message,
                    Instant.now().toString());
        }
    }

    /**
     * Registers a subscriber.
     *
     * @param userId   the connected user (used for logging and cleanup)
     * @param authorId the author whose tickets this subscriber may see, or {@code null} for admins
     */
    public SseEmitter subscribe(UUID userId, UUID authorId, boolean admin) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        UUID id = UUID.randomUUID();
        subscriptions.put(id, new Subscription(id, emitter, authorId, admin));

        Runnable cleanup = () -> {
            subscriptions.remove(id);
            log.debug("SSE subscriber {} disconnected (user {})", id, userId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(new TicketStreamEvent("CONNECTED", null, null, null, null, null,
                            "Live updates enabled", Instant.now().toString())));
        } catch (IOException ex) {
            subscriptions.remove(id);
        }
        return emitter;
    }

    /**
     * Publishes a ticket event to every admin subscriber and to the author who
     * owns the ticket.
     */
    public void publish(Ticket ticket, String type, String message) {
        TicketStreamEvent event = TicketStreamEvent.from(ticket, type, message);
        UUID ticketAuthorId = ticket.getAuthor().getId();

        subscriptions.values().removeIf(subscription -> {
            boolean shouldReceive = subscription.admin()
                    || (subscription.authorId() != null && subscription.authorId().equals(ticketAuthorId));
            if (!shouldReceive) {
                return false;
            }
            try {
                subscription.emitter().send(SseEmitter.event().name("ticket").data(event));
                return false;
            } catch (IOException | IllegalStateException ex) {
                log.debug("Dropping SSE subscriber {}: {}", subscription.id(), ex.getMessage());
                return true;
            }
        });
    }

    /**
     * Keeps proxies from closing idle connections.
     */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        subscriptions.values().removeIf(subscription -> {
            try {
                subscription.emitter().send(SseEmitter.event().comment("keep-alive"));
                return false;
            } catch (IOException | IllegalStateException ex) {
                return true;
            }
        });
    }

    public int activeSubscribers() {
        return subscriptions.size();
    }
}
