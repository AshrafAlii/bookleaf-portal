package com.bookleaf.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A support ticket raised by an author against one of their books (or account-wide).
 */
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** Human-readable reference, e.g. {@code BL-000042}. */
    @Column(name = "ticket_code", nullable = false, unique = true, length = 30)
    private String ticketCode;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    /** {@code null} means the ticket is a general / account-level question. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketCategory category = TicketCategory.GENERAL_INQUIRY;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false, length = 20)
    private DecisionSource categorySource = DecisionSource.AI;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_source", nullable = false, length = 20)
    private DecisionSource prioritySource = DecisionSource.AI;

    /**
     * Numeric mirror of {@link #priority} so the admin queue can sort by urgency in SQL
     * (CRITICAL=4 ... LOW=1). Enum names sort alphabetically, which would put
     * CRITICAL and HIGH after LOW and MEDIUM.
     */
    @Column(name = "priority_rank", nullable = false)
    private int priorityRank = 2;

    @Column(name = "priority_rationale", length = 600)
    private String priorityRationale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Ticket() {
    }

    public Ticket(String ticketCode, Author author, Book book, String subject, String description,
                  String attachmentName) {
        this.ticketCode = ticketCode;
        this.author = author;
        this.book = book;
        this.subject = subject;
        this.description = description;
        this.attachmentName = attachmentName;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setFirstResponseAt(Instant firstResponseAt) {
        this.firstResponseAt = firstResponseAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public void assignTo(User admin) {
        this.assignedTo = admin;
        touch();
    }

    public void changeStatus(TicketStatus newStatus) {
        this.status = newStatus;
        Instant now = Instant.now();
        if (newStatus == TicketStatus.RESOLVED && resolvedAt == null) {
            this.resolvedAt = now;
        }
        if (newStatus == TicketStatus.CLOSED && closedAt == null) {
            this.closedAt = now;
        }
        touch();
    }

    public void markFirstResponse() {
        if (firstResponseAt == null) {
            this.firstResponseAt = Instant.now();
        }
        touch();
    }

    public void applyDecision(TicketCategory category, TicketPriority priority, String rationale,
                              DecisionSource source) {
        this.category = category;
        this.categorySource = source;
        this.priority = priority;
        this.prioritySource = source;
        this.priorityRationale = rationale;
        this.priorityRank = priority.weight();
        touch();
    }

    public void overrideCategory(TicketCategory category) {
        this.category = category;
        this.categorySource = DecisionSource.MANUAL;
        touch();
    }

    public void overridePriority(TicketPriority priority) {
        this.priority = priority;
        this.prioritySource = DecisionSource.MANUAL;
        this.priorityRank = priority.weight();
        touch();
    }

    public UUID getId() {
        return id;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public Author getAuthor() {
        return author;
    }

    public Book getBook() {
        return book;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public DecisionSource getCategorySource() {
        return categorySource;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public DecisionSource getPrioritySource() {
        return prioritySource;
    }

    public int getPriorityRank() {
        return priorityRank;
    }

    public String getPriorityRationale() {
        return priorityRationale;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFirstResponseAt() {
        return firstResponseAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
