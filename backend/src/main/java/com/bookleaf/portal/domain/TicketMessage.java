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
import org.hibernate.annotations.UuidGenerator;

/**
 * A message in a ticket thread.
 *
 * <p>Two flavours share the table: public replies (visible to the author) and
 * internal notes (staff-only). The API layer is the single place that decides
 * which flavour a given viewer may see.</p>
 */
@Entity
@Table(name = "ticket_message")
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Staff member who wrote the message; {@code null} for author-raised queries. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User authorUser;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** {@code true} => internal note, never exposed to the author. */
    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TicketMessage() {
    }

    public TicketMessage(Ticket ticket, User authorUser, String body, boolean internal) {
        this.ticket = ticket;
        this.authorUser = authorUser;
        this.body = body;
        this.internal = internal;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getAuthorUser() {
        return authorUser;
    }

    public String getBody() {
        return body;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
