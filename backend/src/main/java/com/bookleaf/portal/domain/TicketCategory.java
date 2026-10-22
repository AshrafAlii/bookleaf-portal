package com.bookleaf.portal.domain;

/**
 * The six categories the brief mandates. The wire format is the enum name; the
 * label is what the UI and the AI prompt use.
 */
public enum TicketCategory {
    ROYALTY_PAYMENTS("Royalty & Payments"),
    ISBN_METADATA("ISBN & Metadata Issues"),
    PRINTING_QUALITY("Printing & Quality"),
    DISTRIBUTION_AVAILABILITY("Distribution & Availability"),
    PRODUCTION_UPDATES("Book Status & Production Updates"),
    GENERAL_INQUIRY("General Inquiry");

    private final String label;

    TicketCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
