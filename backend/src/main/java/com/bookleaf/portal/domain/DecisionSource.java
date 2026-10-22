package com.bookleaf.portal.domain;

/**
 * Where a classification / priority decision came from.
 *
 * <p>{@code AI} means the LLM decided, {@code HEURISTIC} means the deterministic
 * fallback rules decided (the LLM was unavailable), and {@code MANUAL} means a
 * human overrode the machine. Overrides are always shown in the UI so the team
 * can audit model quality.</p>
 */
public enum DecisionSource {
    AI,
    HEURISTIC,
    MANUAL
}
