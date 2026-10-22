package com.bookleaf.portal.domain;

/**
 * Ticket lifecycle. Authors and admins share the same vocabulary.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }

    /** Display label used by the UI and by AI prompt context. */
    public String label() {
        return switch (this) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In Progress";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
        };
    }
}
