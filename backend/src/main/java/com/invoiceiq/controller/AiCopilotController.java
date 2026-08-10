package com.invoiceiq.controller;

import com.invoiceiq.ai.copilot.FinanceCopilotService;
import com.invoiceiq.dto.CopilotDto.ChatRequest;
import com.invoiceiq.dto.CopilotDto.ChatResponse;
import com.invoiceiq.dto.CopilotDto.QuickInsight;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/copilot")
@Tag(name = "AI Copilot", description = "Finance AI Assistant and Intelligence endpoints")
public class AiCopilotController {

    private final FinanceCopilotService copilotService;

    public AiCopilotController(FinanceCopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Ask financial intelligence questions to the Finance Copilot")
    @PreAuthorize("hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER', 'ACCOUNTANT', 'EMPLOYEE', 'VIEWER')")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return copilotService.chat(request);
    }

    @GetMapping("/quick-insights")
    @Operation(summary = "Get high-priority financial alerts and quick recommendations")
    @PreAuthorize("hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER', 'ACCOUNTANT', 'EMPLOYEE', 'VIEWER')")
    public List<QuickInsight> getQuickInsights() {
        return copilotService.getQuickInsights();
    }
}
