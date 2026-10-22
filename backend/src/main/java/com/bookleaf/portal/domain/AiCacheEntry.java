package com.bookleaf.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Content-addressed cache of AI results.
 *
 * <p>Key = hash(purpose + normalised ticket text + knowledge-base version +
 * model + author context). Identical tickets therefore cost nothing on a retry,
 * and a re-classification after a model change naturally misses the cache.</p>
 */
@Entity
@Table(name = "ai_cache_entry")
public class AiCacheEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 160)
    private String cacheKey;

    @Column(nullable = false, length = 40)
    private String purpose;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AiCacheEntry() {
    }

    public AiCacheEntry(String cacheKey, String purpose, String payload) {
        this.cacheKey = cacheKey;
        this.purpose = purpose;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
