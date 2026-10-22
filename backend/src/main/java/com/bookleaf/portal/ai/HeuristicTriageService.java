package com.bookleaf.portal.ai;

import com.bookleaf.portal.domain.DecisionSource;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic classification and priority rules.
 *
 * <p>This is the safety net that keeps the portal useful when the LLM provider is
 * down, rate limited, or simply not configured. It is also the reference
 * behaviour the AI is expected to match — the rules encode the BookLeaf policy
 * from the brief (ISBN errors are high priority, six months of unpaid royalty is
 * critical, and so on).</p>
 *
 * <p>Deliberately simple and inspectable: keyword scoring with an explicit
 * weight per phrase, highest score wins, ties broken by a fixed category order.</p>
 */
@Component
public class HeuristicTriageService {

    /** Category keyword rules. Weights are intentionally blunt — this is a fallback. */
    private static final Map<TicketCategory, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put(TicketCategory.ROYALTY_PAYMENTS, List.of(
                "royalty", "royalties", "payout", "payouts", "payment", "payments", "unpaid", "not received",
                "haven't received", "have not received", "earnings", "settlement", "salary", "revenue",
                "₹", "ruppee", "rupees", "money", "payment failed", "credit", "credited", "withdrawal", "bank"));
        CATEGORY_KEYWORDS.put(TicketCategory.ISBN_METADATA, List.of(
                "isbn", "metadata", "description", "title mismatch", "wrong title", "catalogue", "catalog",
                "duplicate", "book details", "cover image", "author name on"));
        CATEGORY_KEYWORDS.put(TicketCategory.PRINTING_QUALITY, List.of(
                "print quality", "printing", "print", "binding", "misprint", "misprinted", "blurry", "blurred",
                "paper", "pages", "page alignment", "misaligned", "damaged", "torn", "spine", "smudged",
                "reprint", "crease", "ink", "font", "defective", "poor quality", "copy quality"));
        CATEGORY_KEYWORDS.put(TicketCategory.DISTRIBUTION_AVAILABILITY, List.of(
                "unavailable", "not available", "out of stock", "stock", "listing", "listed", "flipkart",
                "amazon", "amazon us", "amazon uk", "where to buy", "cannot find my book", "can't find my book",
                "shipping", "delivery", "bookleaf store", "not showing", "missing on"));
        CATEGORY_KEYWORDS.put(TicketCategory.PRODUCTION_UPDATES, List.of(
                "typesetting", "type setting", "proofread", "proofreading", "proofs", "proof", "cover design",
                "production", "manuscript", "editing", "in production", "still in", "publication date",
                "when will it be published", "delay", "delayed", "stage", "isbn assignment"));
        CATEGORY_KEYWORDS.put(TicketCategory.GENERAL_INQUIRY, List.of(
                "bio", "biography", "thank you", "thanks", "hello", "hi ", "greetings", "how do i",
                "can i update", "general question", "suggestion", "feature request"));
    }

    /** Explicit high-signal phrases that force a category regardless of other words. */
    private static final List<String[]> CATEGORY_PHRASES = List.of(
            new String[] {"isbn", "ISBN_METADATA"},
            new String[] {"wrong isbn", "ISBN_METADATA"},
            new String[] {"different isbn", "ISBN_METADATA"},
            new String[] {"royalty", "ROYALTY_PAYMENTS"},
            new String[] {"royalties", "ROYALTY_PAYMENTS"},
            new String[] {"payout", "ROYALTY_PAYMENTS"},
            new String[] {"misprint", "PRINTING_QUALITY"},
            new String[] {"print quality", "PRINTING_QUALITY"},
            new String[] {"binding", "PRINTING_QUALITY"},
            new String[] {"blurry", "PRINTING_QUALITY"},
            new String[] {"currently unavailable", "DISTRIBUTION_AVAILABILITY"},
            new String[] {"out of stock", "DISTRIBUTION_AVAILABILITY"},
            new String[] {"typesetting", "PRODUCTION_UPDATES"},
            new String[] {"proofread", "PRODUCTION_UPDATES"},
            new String[] {"cover design", "PRODUCTION_UPDATES"});

    public TriageResult classify(String subject, String description) {
        String text = ((subject == null ? "" : subject) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);

        Map<TicketCategory, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<TicketCategory, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    score += keyword.contains(" ") ? 3 : 2;
                }
            }
            scores.put(entry.getKey(), score);
        }

        for (String[] phrase : CATEGORY_PHRASES) {
            if (text.contains(phrase[0])) {
                scores.merge(TicketCategory.valueOf(phrase[1]), 6, Integer::sum);
            }
        }

        TicketCategory category = TicketCategory.GENERAL_INQUIRY;
        int best = 0;
        for (Map.Entry<TicketCategory, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                category = entry.getKey();
            }
        }

        TicketPriority priority = priorityFor(text, category, best);
        List<String> signals = signals(text, category);
        String rationale = rationale(category, priority, signals);
        return TriageResult.of(category, priority, rationale, DecisionSource.HEURISTIC, signals,
                "Classified by built-in rules because the AI provider was unavailable.");
    }

    private TicketPriority priorityFor(String text, TicketCategory category, int categoryScore) {
        // Critical: money that is clearly overdue.
        boolean royaltyOverdue = text.contains("royalty") || text.contains("royalties") || text.contains("payout");
        boolean longSilence = containsAny(text,
                "6 months", "six months", "8 months", "eight months", "never received", "haven't received",
                "have not received", "not received any", "still haven't", "still have not", "no payment",
                "since", "long time", "months ago");
        boolean explicitUnpaid = containsAny(text, "not been paid", "not paid", "unpaid", "payment failed");

        if (royaltyOverdue && (longSilence || explicitUnpaid)) {
            return TicketPriority.CRITICAL;
        }

        // High: ISBN errors are high priority per BookLeaf policy; printing defects
        // and availability problems are live, time-sensitive issues.
        if (category == TicketCategory.ISBN_METADATA) {
            return TicketPriority.HIGH;
        }
        if (category == TicketCategory.PRINTING_QUALITY) {
            return TicketPriority.HIGH;
        }
        if (category == TicketCategory.DISTRIBUTION_AVAILABILITY && containsAny(text,
                "unavailable", "out of stock", "not available", "cannot find", "can't find")) {
            return TicketPriority.HIGH;
        }
        if (category == TicketCategory.PRODUCTION_UPDATES && containsAny(text,
                "3 weeks", "three weeks", "weeks", "delayed", "still in", "stuck", "no update")) {
            return TicketPriority.HIGH;
        }
        if (royaltyOverdue && containsAny(text, "amount", "less than", "too low", "incorrect", "wrong amount")) {
            return TicketPriority.HIGH;
        }

        // Low: purely informational asks.
        if (containsAny(text, "can i update my bio", "update my bio", "just wanted", "curiosity",
                "is it possible to know", "any update on", "thank you for")) {
            return TicketPriority.LOW;
        }

        return categoryScore == 0 ? TicketPriority.LOW : TicketPriority.MEDIUM;
    }

    private List<String> signals(String text, TicketCategory category) {
        List<String> found = new ArrayList<>();
        for (String keyword : CATEGORY_KEYWORDS.get(category)) {
            if (text.contains(keyword) && found.size() < 5) {
                found.add(keyword);
            }
        }
        return found;
    }

    private String rationale(TicketCategory category, TicketPriority priority, List<String> signals) {
        String matched = signals.isEmpty() ? "no strong keyword" : String.join(", ", signals);
        return "Rule-based match on " + matched + " → " + category.label() + " / " + priority.label();
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
