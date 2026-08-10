package com.invoiceiq.controller;

import com.invoiceiq.ai.insights.CostSavingEngineService;
import com.invoiceiq.dto.AiIntelligenceDto.CostSavingRecommendation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/insights")
@Tag(name = "AI Insights", description = "AI Cost Optimization and Intelligence recommendations")
public class AiInsightsController {

    private final CostSavingEngineService costSavingEngineService;

    public AiInsightsController(CostSavingEngineService costSavingEngineService) {
        this.costSavingEngineService = costSavingEngineService;
    }

    @GetMapping("/cost-savings")
    @Operation(summary = "Get actionable AI cost-saving and optimization recommendations")
    @PreAuthorize("hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER', 'ACCOUNTANT', 'EMPLOYEE', 'VIEWER')")
    public List<CostSavingRecommendation> getCostSavings() {
        return costSavingEngineService.generateRecommendations();
    }
}
