package com.invoiceiq.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class CopilotDto {

    public record ChatRequest(
        String message,
        List<ChatMessage> history
    ) {}

    public record ChatMessage(
        String role, // "user" or "assistant"
        String content
    ) {}

    public record ChatResponse(
        String answer,
        String intent,
        List<String> suggestedFollowUps,
        Map<String, Object> metrics,
        Instant timestamp
    ) {}

    public record QuickInsight(
        String title,
        String summary,
        String category,
        String severity, // "INFO", "WARNING", "SAVINGS", "CRITICAL"
        String actionUrl
    ) {}
}
