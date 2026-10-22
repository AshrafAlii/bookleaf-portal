package com.bookleaf.portal.web;

import com.bookleaf.portal.ai.AiGateway;
import com.bookleaf.portal.ai.KnowledgeBaseService;
import com.bookleaf.portal.dto.AdminDtos;
import com.bookleaf.portal.dto.AdminDtos.MetaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reference data used by the UI. Public on purpose: the login screen can tell
 * the reviewer whether the AI integration is live before they sign in.
 */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta", description = "Reference data and integration status")
public class MetaController {

    private final AiGateway aiGateway;
    private final KnowledgeBaseService knowledgeBaseService;

    public MetaController(AiGateway aiGateway, KnowledgeBaseService knowledgeBaseService) {
        this.aiGateway = aiGateway;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    @Operation(summary = "Ticket categories, priorities, statuses and AI provider status")
    public MetaResponse meta() {
        return new MetaResponse(
                AdminDtos.categoryLabels(),
                AdminDtos.priorityLabels(),
                AdminDtos.statusLabels(),
                aiGateway.isAiEnabled(),
                aiGateway.providerName(),
                aiGateway.isAiEnabled() ? aiGateway.modelName("DRAFT") : null);
    }

    @GetMapping("/knowledge-base")
    @Operation(summary = "Knowledge base sections and size (shown in the admin knowledge panel)")
    public KnowledgeBaseInfo knowledgeBase() {
        return new KnowledgeBaseInfo(
                knowledgeBaseService.allSections().stream().map(section -> new KnowledgeBaseSectionInfo(
                        section.title(),
                        section.approxTokens(),
                        section.body())).toList(),
                knowledgeBaseService.totalTokens(),
                knowledgeBaseService.version());
    }

    public record KnowledgeBaseInfo(List<KnowledgeBaseSectionInfo> sections, int totalTokens, String version) {
    }

    public record KnowledgeBaseSectionInfo(String title, int approxTokens, String body) {
    }
}
