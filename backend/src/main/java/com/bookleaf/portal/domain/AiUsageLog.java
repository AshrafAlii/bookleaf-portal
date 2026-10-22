package com.bookleaf.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per LLM call. Kept deliberately small so the admin view can show
 * token spend, latency and failure rate — cost awareness you can actually see.
 */
@Entity
@Table(name = "ai_usage_log")
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code TRIAGE}, {@code DRAFT} or {@code HEALTH}. */
    @Column(nullable = false, length = 40)
    private String purpose;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(length = 60)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    /** {@code true} when the answer was served from the local cache (no billable call). */
    @Column(nullable = false)
    private boolean cached;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(nullable = false)
    private boolean success = true;

    @Column(name = "error_message", length = 600)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AiUsageLog() {
    }

    public AiUsageLog(String purpose, String provider, String model, Integer promptTokens, Integer completionTokens,
                      boolean cached, Integer latencyMs, boolean success, String errorMessage) {
        this.purpose = purpose;
        this.provider = provider;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.cached = cached;
        this.latencyMs = latencyMs;
        this.success = success;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public boolean isCached() {
        return cached;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
