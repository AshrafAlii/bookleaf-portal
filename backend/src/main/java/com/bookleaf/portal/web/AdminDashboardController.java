package com.bookleaf.portal.web;

import com.bookleaf.portal.dto.AdminDtos.AdminDashboardResponse;
import com.bookleaf.portal.dto.AdminDtos.AiUsageEntry;
import com.bookleaf.portal.dto.AdminDtos.AiUsageSummary;
import com.bookleaf.portal.dto.AdminDtos.QueueStats;
import com.bookleaf.portal.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin dashboard", description = "Queue health, AI usage and cost visibility")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Queue statistics, urgent tickets and AI usage")
    public AdminDashboardResponse dashboard() {
        return adminDashboardService.dashboard();
    }

    @GetMapping("/stats")
    @Operation(summary = "Queue statistics only")
    public QueueStats stats() {
        return adminDashboardService.queueStats();
    }

    @GetMapping("/ai-usage")
    @Operation(summary = "AI usage and cost summary")
    public AiUsageSummary aiUsage() {
        return adminDashboardService.aiUsage();
    }

    @GetMapping("/ai-usage/recent")
    @Operation(summary = "Recent individual AI calls with token usage and latency")
    public List<AiUsageEntry> recentAiUsage(@RequestParam(defaultValue = "25") int limit) {
        return adminDashboardService.recentAiUsage(Math.min(Math.max(limit, 1), 100));
    }
}
