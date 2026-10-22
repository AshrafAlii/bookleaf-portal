package com.bookleaf.portal.domain;

/**
 * Priority assigned by the AI triage model, overridable by an admin.
 */
public enum TicketPriority {
    CRITICAL("Critical", 4),
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1);

    private final String label;
    private final int weight;

    TicketPriority(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    public int weight() {
        return weight;
    }
}
