package com.bookleaf.portal.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookleaf.portal.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Tests for knowledge-base parsing and retrieval — the mechanism that keeps
 * prompts small.
 */
class KnowledgeBaseServiceTest {

    private KnowledgeBaseService service() {
        AppProperties properties = new AppProperties();
        properties.getAi().setKnowledgeBasePath("classpath:knowledge/knowledge-base.md");
        return new KnowledgeBaseService(properties, new DefaultResourceLoader());
    }

    @Test
    @DisplayName("the knowledge base is parsed into sections")
    void parsesSections() {
        KnowledgeBaseService service = service();

        assertThat(service.allSections()).hasSizeGreaterThan(10);
        assertThat(service.allSections()).anyMatch(section -> section.title().equals("Royalty Policy"));
        assertThat(service.totalTokens()).isGreaterThan(500);
        assertThat(service.version()).isNotBlank();
    }

    @Test
    @DisplayName("retrieval returns only the most relevant sections plus the tone guide")
    void retrievalIsSelective() {
        KnowledgeBaseService service = service();

        KnowledgeBaseService.Retrieval retrieval = service.retrieve(
                "My royalty payment has not been credited and I need to know about the payout threshold",
                2);

        assertThat(retrieval.sections()).hasSizeLessThanOrEqualTo(3);
        assertThat(retrieval.sections()).anyMatch(s -> s.title().equals("Royalty Policy"));
        assertThat(retrieval.sections()).anyMatch(s -> s.title().equals("Communication Tone Guidelines"));
    }

    @Test
    @DisplayName("retrieval pulls the ISBN policy for an ISBN query")
    void retrievalMatchesTheRightPolicy() {
        KnowledgeBaseService service = service();

        KnowledgeBaseService.Retrieval retrieval = service.retrieve(
                "The ISBN on the Amazon listing is wrong and does not match my paperback",
                3);

        assertThat(retrieval.sections()).anyMatch(s -> s.title().equals("ISBN Policy"));
    }

    @Test
    @DisplayName("retrieved context is much smaller than the whole knowledge base")
    void retrievalSavesTokens() {
        KnowledgeBaseService service = service();

        KnowledgeBaseService.Retrieval retrieval = service.retrieve(
                "I need a free reprint because the binding on my copies is defective", 2);

        assertThat(retrieval.tokens()).isLessThan(service.totalTokens());
    }

    @Test
    @DisplayName("the calibration example for a category can be found")
    void calibrationExampleIsFound() {
        KnowledgeBaseService service = service();

        List<KnowledgeSection> examples = service.calibrationExamples("Royalty & Payments");

        assertThat(examples).isNotEmpty();
        assertThat(examples.get(0).title()).contains("Royalty & Payments");
        assertThat(service.calibrationExamples("Nonexistent Category")).isEmpty();
    }
}
