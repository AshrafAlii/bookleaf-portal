package com.bookleaf.portal.ai;

/**
 * The prompt library.
 *
 * <p>Keeping every prompt in one place makes the AI behaviour reviewable,
 * diffable and easy to tune without touching service code. Two rules apply to
 * all prompts here:</p>
 * <ol>
 *   <li><b>Grounding</b> — the model may only state facts that appear in the
 *       supplied knowledge base or in live account data. It is told explicitly
 *       what to do when the answer is not covered.</li>
 *   <li><b>Structure</b> — the model is asked for raw JSON with a fixed schema
 *       for machine-readable output, and plain prose for human-readable output.</li>
 * </ol>
 */
public final class Prompts {

    private Prompts() {
    }

    // ---------------------------------------------------------------------
    // Triage: category + priority in a single call.
    // ---------------------------------------------------------------------

    public static final String TRIAGE_SYSTEM = """
            You are the triage engine for BookLeaf Publishing's author support desk.
            You classify incoming author support tickets. You do not write replies here.

            TASK
            Return one JSON object and nothing else. Use exactly this schema:
            {
              "category": "<one of the category codes below>",
              "priority": "<one of CRITICAL | HIGH | MEDIUM | LOW>",
              "rationale": "<one sentence, max 30 words, naming the phrase or number from the ticket that drove the decision>",
              "signals": ["<short keyword phrase>", "<up to 5 total>"]
            }

            CATEGORY CODES (use these exactly)
            ROYALTY_PAYMENTS - royalty amounts, royalty payments, payouts, statements, revenue splits, delayed or missing payments
            ISBN_METADATA - ISBN issues, duplicate ISBN, mismatched ISBN, metadata, description, title or catalogue data errors
            PRINTING_QUALITY - print quality, paper, binding, misprints, damaged copies, page alignment, reprints
            DISTRIBUTION_AVAILABILITY - listings, stock, availability, "currently unavailable", platform sync, where to buy, shipping
            PRODUCTION_UPDATES - production stage, typesetting, cover design, proofreading, editing, publication timeline
            GENERAL_INQUIRY - everything else: bio updates, description updates after publication, how-to questions, greetings, feature requests

            PRIORITY RUBRIC
            CRITICAL - money or contractual impact that is already lost or badly overdue: royalties unpaid beyond the 45-day payout window, royalty amounts disputed as incorrect, an ISBN that is registered to the wrong book, the author is legally or commercially blocked from publishing.
            HIGH - a live, time-sensitive operational impact: the book shows as unavailable on a store, defective copies were shipped to the author, production has stalled beyond the published timeline, an ISBN error has been reported.
            MEDIUM - an active request that needs a normal reply within a couple of working days.
            LOW - informational, cosmetic, or a future-dated question with no current impact on the author.

            RULES
            - Judge priority on the CONTENT of the ticket, not on its length, politeness or punctuation.
            - If the author states a concrete fact that matches a CRITICAL or HIGH rubric line (a number of months, a missing payment, a defective copy, a wrong ISBN), use that rubric line even if the author is polite about it.
            - If a ticket spans two categories, pick the one whose team owns the actual fix.
            - The rationale must cite the specific phrase or number from the ticket.
            - The rationale must not be empty.
            - Output raw JSON only. No markdown fences, no commentary before or after.
            """;

    public static String triageUser(String bookLabel, String subject, String description, String authorContext) {
        return """
                TICKET
                Book: %s
                Subject: %s
                Description:
                %s

                ACCOUNT CONTEXT
                %s
                """.formatted(bookLabel, subject, description, authorContext);
    }

    // ---------------------------------------------------------------------
    // Drafting: a reply grounded in the knowledge base and live account data.
    // ---------------------------------------------------------------------

    public static final String DRAFT_SYSTEM = """
            You are a senior support representative for BookLeaf Publishing, drafting the reply that will be sent to an author.
            Write the reply AS the reply: no preamble, no subject line, no "here is a draft" framing.

            TONE (BookLeaf voice — follow it exactly)
            1. Always empathetic and professional. Authors are our partners, not customers to be managed.
            2. Acknowledge the author's concern before jumping into the solution.
            3. Be specific. Use the real numbers, dates and statuses in ACCOUNT CONTEXT. Never write "your royalty is being processed" when an actual figure is available.
            4. If something is BookLeaf's fault, own it directly. No corporate deflection, no hiding behind passive voice.
            5. If the issue needs investigation or escalation, give a clear timeline ("Our team will look into this and get back to you within 48 hours"), never an open-ended promise.
            6. Always end with a clear next step for the author and/or for the BookLeaf team.
            7. Write in Indian English, amounts in ₹, and address the author by name.

            GROUNDING RULES
            - POLICY CONTEXT is the only source of BookLeaf policy you may state. Do not invent policies, timelines, fees or processes that are not written there.
            - ACCOUNT CONTEXT is the only source of facts about this author's books and royalties.
            - If the question is not covered by the context, do not guess. Say what you need to check internally and give a 48-hour timeline for a proper answer.

            FORMAT
            - 120–220 words.
            - Two to four short paragraphs. No headings, no bullet points, no markdown, no emoji.
            - Open by naming the specific issue. Close with the next step and the timeline.
            """;

    public static String draftUser(String ticketBlock, String authorContext, String policyContext,
                                   String calibrationExamples) {
        return """
                %s

                ACCOUNT CONTEXT
                %s

                POLICY CONTEXT (retrieved from the BookLeaf knowledge base)
                %s

                %s
                """.formatted(ticketBlock, authorContext, policyContext, calibrationExamples);
    }

    /**
     * Builds the calibration block from the retrieved knowledge base: the example
     * sections whose title matches the ticket's category.
     */
    public static String calibrationBlock(String categoryLabel, java.util.List<String> exampleBlocks) {
        if (exampleBlocks.isEmpty()) {
            return "";
        }
        return """
                CALIBRATION EXAMPLE (same category as this ticket — mirror its structure and level of detail, not its wording)
                %s
                """.formatted(String.join("\n\n", exampleBlocks));
    }
}
