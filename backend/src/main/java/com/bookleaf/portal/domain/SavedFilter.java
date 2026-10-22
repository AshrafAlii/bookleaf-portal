package com.bookleaf.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A named queue filter owned by one admin.
 *
 * <p>The filter set is stored as a JSON blob rather than a column per filter:
 * the set of filters changes as the queue grows, and a JSON blob keeps the
 * schema stable without a migration every time a filter is added.</p>
 */
@Entity
@Table(name = "saved_filter")
public class SavedFilter {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    /** Serialised {@code QueueFilters} JSON. */
    @Column(nullable = false, columnDefinition = "text")
    private String filters;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SavedFilter() {
    }

    public SavedFilter(User owner, String name, String filters) {
        this.owner = owner;
        this.name = name;
        this.filters = filters;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getFilters() {
        return filters;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
