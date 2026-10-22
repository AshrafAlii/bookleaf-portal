package com.bookleaf.portal.ai;

import java.util.List;

/**
 * A retrievable slice of the BookLeaf knowledge base.
 */
public record KnowledgeSection(String title, String body, int approxTokens, List<String> keywords) {

    public int approxTokens() {
        return approxTokens;
    }
}
