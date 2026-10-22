package com.bookleaf.portal.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic fallback classifier — the path that runs
 * whenever the LLM provider is unavailable. These rules encode the BookLeaf
 * policy from the brief, so they are worth pinning down.
 */
class HeuristicTriageServiceTest {

    private final HeuristicTriageService service = new HeuristicTriageService();

    @Test
    @DisplayName("a royalty query that mentions months of silence is critical")
    void royaltyOverdueIsCritical() {
        TriageResult result = service.classify(
                "Royalty not received for 6 months",
                "I published 6 months ago and I have not received any royalty payment at all.");

        assertThat(result.category()).isEqualTo(TicketCategory.ROYALTY_PAYMENTS);
        assertThat(result.priority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(result.source()).isEqualTo(com.bookleaf.portal.domain.DecisionSource.HEURISTIC);
    }

    @Test
    @DisplayName("an ISBN mismatch is high priority, per BookLeaf policy")
    void isbnMismatchIsHighPriority() {
        TriageResult result = service.classify(
                "Wrong ISBN on Amazon",
                "The Amazon listing shows a different ISBN than the one printed on my paperback.");

        assertThat(result.category()).isEqualTo(TicketCategory.ISBN_METADATA);
        assertThat(result.priority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    @DisplayName("a printing defect is classified as printing and quality")
    void printingDefectIsPrintingQuality() {
        TriageResult result = service.classify(
                "Poor print quality on my author copies",
                "The images are blurry and the pages are misaligned, and the binding is damaged.");

        assertThat(result.category()).isEqualTo(TicketCategory.PRINTING_QUALITY);
        assertThat(result.priority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    @DisplayName("an unavailable listing is classified as distribution")
    void unavailableListingIsDistribution() {
        TriageResult result = service.classify(
                "Book shows as Currently Unavailable on Amazon",
                "My book is published but Amazon says it is currently unavailable.");

        assertThat(result.category()).isEqualTo(TicketCategory.DISTRIBUTION_AVAILABILITY);
    }

    @Test
    @DisplayName("a stalled production stage is classified as production")
    void stalledProductionIsProduction() {
        TriageResult result = service.classify(
                "Still in typesetting after three weeks",
                "It has been 3 weeks and my book is still in typesetting. When will it be done?");

        assertThat(result.category()).isEqualTo(TicketCategory.PRODUCTION_UPDATES);
        assertThat(result.priority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    @DisplayName("a metadata question falls into general inquiry and low priority")
    void metadataQuestionIsGeneralAndLow() {
        TriageResult result = service.classify(
                "Can I update my author bio?",
                "Can I update the description of my book after it is already live?");

        assertThat(result.category()).isEqualTo(TicketCategory.GENERAL_INQUIRY);
        assertThat(result.priority()).isEqualTo(TicketPriority.LOW);
    }

    @Test
    @DisplayName("an unrecognised message still returns a usable classification")
    void unknownMessageStillClassifies() {
        TriageResult result = service.classify("Hello", "Just saying hi, nothing specific.");

        assertThat(result.category()).isNotNull();
        assertThat(result.priority()).isNotNull();
        assertThat(result.rationale()).isNotBlank();
    }
}
