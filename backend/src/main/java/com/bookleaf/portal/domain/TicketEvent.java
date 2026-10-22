package com.bookleaf.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Immutable audit entry for a ticket: creation, status change, assignment,
 * category/priority override, reply sent, internal note added.
 */
@Entity
@Table(name = "ticket_event")
public class TicketEvent {

    public static final String CREATED = "CREATED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String CATEGORY_OVERRIDDEN = "CATEGORY_OVERRIDDEN";
    public static final String PRIORITY_OVERRIDDEN = "PRIORITY_OVERRIDDEN";
    public static final String REPLY_SENT = "REPLY_SENT";
    public static final String NOTE_ADDED = "NOTE_ADDED";
    public static final String AUTHOR_REPLIED = "AUTHOR_REPLIED";
    public static final String AI_CLASSIFIED = "AI_CLASSIFIED";
    public static final String AI_DRAFT_GENERATED = "AI_DRAFT_GENERATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TicketEvent() {
    }

    public TicketEvent(Ticket ticket, User actorUser, String eventType, String detail) {
        this.ticket = ticket;
        this.actorUser = actorUser;
        this.eventType = eventType;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getActorUser() {
        return actorUser;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
