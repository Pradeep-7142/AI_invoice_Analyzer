package com.invoiceiq.ai.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.ai.AiProperties;
import com.invoiceiq.analytics.AnalyticsService;
import com.invoiceiq.dto.AnalyticsSummaryResponse;
import com.invoiceiq.dto.BudgetStatusResponse;
import com.invoiceiq.dto.CashFlowForecastResponse;
import com.invoiceiq.dto.CategorySpendResponse;
import com.invoiceiq.dto.CopilotDto.ChatMessage;
import com.invoiceiq.dto.CopilotDto.ChatRequest;
import com.invoiceiq.dto.CopilotDto.ChatResponse;
import com.invoiceiq.dto.CopilotDto.QuickInsight;
import com.invoiceiq.dto.MonthlySpendPoint;
import com.invoiceiq.dto.VendorSpendResponse;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.forecast.ForecastService;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.security.CurrentUser;
import com.invoiceiq.service.BudgetService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class FinanceCopilotService {

    private static final Logger log = LoggerFactory.getLogger(FinanceCopilotService.class);

    private final AnalyticsService analyticsService;
    private final BudgetService budgetService;
    private final ForecastService forecastService;
    private final InvoiceRepository invoiceRepository;
    private final CurrentUser currentUser;
    private final AiProperties aiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FinanceCopilotService(
        AnalyticsService analyticsService,
        BudgetService budgetService,
        ForecastService forecastService,
        InvoiceRepository invoiceRepository,
        CurrentUser currentUser,
        AiProperties aiProperties,
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper
    ) {
        this.analyticsService = analyticsService;
        this.budgetService = budgetService;
        this.forecastService = forecastService;
        this.invoiceRepository = invoiceRepository;
        this.currentUser = currentUser;
        this.aiProperties = aiProperties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ChatResponse chat(ChatRequest request) {
        String query = request.message() == null ? "" : request.message().trim();
        Map<String, Object> financialContext = collectFinancialContext();

        if (aiProperties.isLlmEnabled() && !query.isBlank()) {
            try {
                return callLlmCopilot(query, request.history(), financialContext);
            } catch (Exception e) {
                log.warn("LLM Copilot call failed ({}). Falling back to grounded rule engine.", e.getMessage());
            }
        }

        return generateGroundedRuleResponse(query, financialContext);
    }

    @Transactional(readOnly = true)
    public List<QuickInsight> getQuickInsights() {
        List<QuickInsight> insights = new ArrayList<>();
        UUID organizationId = currentUser.organizationId();

        // 1. Overdue Invoices
        List<Invoice> overdue = invoiceRepository.findByOrganizationId(organizationId).stream()
            .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
            .toList();
        if (!overdue.isEmpty()) {
            BigDecimal overdueSum = overdue.stream()
                .map(Invoice::getTotalAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            insights.add(new QuickInsight(
                "Overdue Invoices Alert",
                String.format("%d invoice(s) are currently overdue totaling %s. Review payment schedules.", overdue.size(), formatMoney(overdueSum)),
                "PAYMENTS",
                "CRITICAL",
                "/invoices"
            ));
        }

        // 2. Budget Overruns
        List<BudgetStatusResponse> budgetStatuses = budgetService.status(YearMonth.now());
        List<BudgetStatusResponse> exceeded = budgetStatuses.stream().filter(BudgetStatusResponse::overBudget).toList();
        if (!exceeded.isEmpty()) {
            String categories = exceeded.stream().map(BudgetStatusResponse::category).reduce((a, b) -> a + ", " + b).orElse("");
            insights.add(new QuickInsight(
                "Budget Exceeded",
                String.format("Monthly budget exceeded in: %s for current month.", categories),
                "BUDGET",
                "WARNING",
                "/budgets"
            ));
        }

        // 3. Pending Approvals
        long pendingApprovalCount = invoiceRepository.findByOrganizationId(organizationId).stream()
            .filter(i -> i.getStatus() == InvoiceStatus.PENDING_APPROVAL)
            .count();
        if (pendingApprovalCount > 0) {
            insights.add(new QuickInsight(
                "Action Required: Approvals",
                String.format("%d invoice(s) are awaiting finance manager or admin approval.", pendingApprovalCount),
                "APPROVALS",
                "INFO",
                "/dashboard"
            ));
        }

        // 4. Upcoming Cash Obligations
        CashFlowForecastResponse cashFlow = forecastService.cashFlow(4);
        if (cashFlow.totalDueUnscheduled().compareTo(BigDecimal.ZERO) > 0 || cashFlow.totalScheduled().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalNextMonth = cashFlow.totalScheduled().add(cashFlow.totalDueUnscheduled());
            insights.add(new QuickInsight(
                "30-Day Cash Obligations",
                String.format("Estimated outgoing payments for the next 4 weeks: %s.", formatMoney(totalNextMonth)),
                "FORECAST",
                "INFO",
                "/analytics"
            ));
        }

        if (insights.isEmpty()) {
            insights.add(new QuickInsight(
                "Financial Health Normal",
                "All budgets are within limits and no invoices are overdue.",
                "GENERAL",
                "INFO",
                "/dashboard"
            ));
        }

        return insights;
    }

    private Map<String, Object> collectFinancialContext() {
        UUID orgId = currentUser.organizationId();
        AnalyticsSummaryResponse summary = analyticsService.summary(6);
        List<CategorySpendResponse> categories = analyticsService.categorySpend(6);
        List<VendorSpendResponse> topVendors = analyticsService.topVendors(6, 5);
        List<BudgetStatusResponse> budgets = budgetService.status(YearMonth.now());
        CashFlowForecastResponse cashFlow = forecastService.cashFlow(4);

        List<Invoice> allInvoices = invoiceRepository.findByOrganizationId(orgId);
        long overdueCount = allInvoices.stream().filter(i -> i.getStatus() == InvoiceStatus.OVERDUE).count();
        long pendingApprovalCount = allInvoices.stream().filter(i -> i.getStatus() == InvoiceStatus.PENDING_APPROVAL).count();
        long needsReviewCount = allInvoices.stream().filter(i -> i.getStatus() == InvoiceStatus.NEEDS_REVIEW).count();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("totalSpendLast6Months", summary.totalSpend());
        ctx.put("invoiceCountLast6Months", summary.invoiceCount());
        ctx.put("averageInvoiceAmount", summary.averageInvoiceAmount());
        ctx.put("totalOutstandingPayable", summary.totalOutstanding());
        ctx.put("overdueInvoicesCount", overdueCount);
        ctx.put("pendingApprovalCount", pendingApprovalCount);
        ctx.put("needsReviewCount", needsReviewCount);
        ctx.put("topVendors", topVendors);
        ctx.put("categorySpend", categories);
        ctx.put("budgetStatuses", budgets);
        ctx.put("cashFlowNext4WeeksScheduled", cashFlow.totalScheduled());
        ctx.put("cashFlowNext4WeeksDueUnscheduled", cashFlow.totalDueUnscheduled());
        return ctx;
    }

    private ChatResponse callLlmCopilot(String query, List<ChatMessage> history, Map<String, Object> financialContext) throws Exception {
        String endpoint = resolveEndpoint();
        String contextJson = objectMapper.writeValueAsString(financialContext);

        String systemPrompt = """
            You are the InvoiceIQ Finance Copilot, an AI financial intelligence assistant for B2B finance teams.
            You have access to the verified, real-time organization financial data provided in the JSON context below.

            RULES:
            1. ONLY answer using the facts and metrics from the verified JSON context.
            2. State numbers clearly with INR / relevant currency symbols.
            3. If asked why spending changed or what needs attention, highlight top categories, vendors, or overdue amounts.
            4. Structure answers with clean Markdown headings, bullet points, and bold key numbers.
            5. NEVER fabricate or hallucinate any numbers or invoices not in the context.

            VERIFIED FINANCIAL DATA CONTEXT:
            """ + contextJson;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg != null && msg.content() != null && !msg.content().isBlank()) {
                    messages.add(Map.of("role", msg.role().equals("assistant") ? "assistant" : "user", "content", msg.content()));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", query));

        Map<String, Object> requestBody = Map.of(
            "model", aiProperties.getModel() != null ? aiProperties.getModel() : "gpt-4o-mini",
            "temperature", 0.2,
            "messages", messages
        );

        String rawResponse = restClient.post()
            .uri(endpoint)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(String.class);

        JsonNode root = objectMapper.readTree(rawResponse);
        String answer = root.path("choices").get(0).path("message").path("content").asText();

        return new ChatResponse(
            answer,
            "LLM_ASSISTED_ANSWER",
            generateFollowUpSuggestions(query),
            financialContext,
            Instant.now()
        );
    }

    private ChatResponse generateGroundedRuleResponse(String query, Map<String, Object> ctx) {
        String lower = query.toLowerCase();
        StringBuilder answer = new StringBuilder();
        String intent = "GENERAL_SUMMARY";

        if (lower.contains("overdue") || lower.contains("late")) {
            intent = "OVERDUE_INVOICES";
            long overdueCount = (Long) ctx.getOrDefault("overdueInvoicesCount", 0L);
            if (overdueCount == 0) {
                answer.append("### ✅ No Overdue Invoices\n\nThere are currently **0 overdue invoices** in your organization. All payable invoices are within their due dates.");
            } else {
                answer.append(String.format("### ⚠️ Overdue Invoices Report\n\nThere are currently **%d overdue invoice(s)** requiring immediate settlement.\n\n", overdueCount));
                answer.append("- Check the [Invoices](/invoices) table filtered by status **OVERDUE** to review specific vendors and scheduled payments.");
            }
        } else if (lower.contains("vendor") || lower.contains("supplier") || lower.contains("who")) {
            intent = "TOP_VENDORS";
            @SuppressWarnings("unchecked")
            List<VendorSpendResponse> vendors = (List<VendorSpendResponse>) ctx.get("topVendors");
            answer.append("### 🏢 Top Vendors by Spend (Last 6 Months)\n\n");
            if (vendors == null || vendors.isEmpty()) {
                answer.append("No vendor spend data recorded in the last 6 months.");
            } else {
                for (int i = 0; i < vendors.size(); i++) {
                    VendorSpendResponse v = vendors.get(i);
                    answer.append(String.format("%d. **%s** (%s): Total Spend **%s** across %d invoice(s) (Avg: %s)\n",
                        i + 1, v.vendorName(), v.category() != null ? v.category() : "General", formatMoney(v.totalSpend()), v.invoiceCount(), formatMoney(v.averageAmount())));
                }
            }
        } else if (lower.contains("budget") || lower.contains("exceed") || lower.contains("limit")) {
            intent = "BUDGET_ANALYSIS";
            @SuppressWarnings("unchecked")
            List<BudgetStatusResponse> budgets = (List<BudgetStatusResponse>) ctx.get("budgetStatuses");
            answer.append("### 📊 Budget Utilization Status (Current Month)\n\n");
            if (budgets == null || budgets.isEmpty()) {
                answer.append("No monthly category budgets configured yet. You can set them up in [Budgets](/budgets).");
            } else {
                for (BudgetStatusResponse b : budgets) {
                    String statusEmoji = b.overBudget() ? "🔴 **EXCEEDED**" : (b.percentUsed() > 80 ? "🟡 **Near Limit**" : "🟢 **Healthy**");
                    answer.append(String.format("- **%s**: Spent **%s** of **%s** limit (%s%% used) — %s\n",
                        b.category(), formatMoney(b.actualSpend()), formatMoney(b.monthlyLimit()), b.percentUsed(), statusEmoji));
                }
            }
        } else if (lower.contains("cash") || lower.contains("outgoing") || lower.contains("forecast") || lower.contains("next")) {
            intent = "CASH_FLOW_FORECAST";
            BigDecimal scheduled = (BigDecimal) ctx.getOrDefault("cashFlowNext4WeeksScheduled", BigDecimal.ZERO);
            BigDecimal dueUnscheduled = (BigDecimal) ctx.getOrDefault("cashFlowNext4WeeksDueUnscheduled", BigDecimal.ZERO);
            BigDecimal total = scheduled.add(dueUnscheduled);

            answer.append("### 💸 Upcoming Cash Obligations (Next 30 Days)\n\n");
            answer.append(String.format("- **Total Expected Outflow**: **%s**\n", formatMoney(total)));
            answer.append(String.format("- **Already Scheduled Payments**: **%s**\n", formatMoney(scheduled)));
            answer.append(String.format("- **Due & Unscheduled Invoices**: **%s**\n\n", formatMoney(dueUnscheduled)));
            answer.append("Visit [Analytics](/analytics) to view week-by-week cash flow distributions.");
        } else if (lower.contains("spend") || lower.contains("expense") || lower.contains("how much")) {
            intent = "SPEND_BREAKDOWN";
            BigDecimal totalSpend = (BigDecimal) ctx.getOrDefault("totalSpendLast6Months", BigDecimal.ZERO);
            int count = (Integer) ctx.getOrDefault("invoiceCountLast6Months", 0);
            BigDecimal avg = (BigDecimal) ctx.getOrDefault("averageInvoiceAmount", BigDecimal.ZERO);
            BigDecimal outstanding = (BigDecimal) ctx.getOrDefault("totalOutstandingPayable", BigDecimal.ZERO);

            answer.append("### 📈 Organizational Spend Summary (Last 6 Months)\n\n");
            answer.append(String.format("- **Total Recorded Spend**: **%s** across **%d** invoices\n", formatMoney(totalSpend), count));
            answer.append(String.format("- **Average Invoice Value**: **%s**\n", formatMoney(avg)));
            answer.append(String.format("- **Total Outstanding Obligations**: **%s**\n\n", formatMoney(outstanding)));

            @SuppressWarnings("unchecked")
            List<CategorySpendResponse> categories = (List<CategorySpendResponse>) ctx.get("categorySpend");
            if (categories != null && !categories.isEmpty()) {
                answer.append("**Spend by Category:**\n");
                for (CategorySpendResponse c : categories) {
                    answer.append(String.format("- %s: **%s** (%d invoices)\n", c.category(), formatMoney(c.totalSpend()), c.invoiceCount()));
                }
            }
        } else {
            intent = "FINANCIAL_OVERVIEW";
            BigDecimal totalSpend = (BigDecimal) ctx.getOrDefault("totalSpendLast6Months", BigDecimal.ZERO);
            BigDecimal outstanding = (BigDecimal) ctx.getOrDefault("totalOutstandingPayable", BigDecimal.ZERO);
            long overdueCount = (Long) ctx.getOrDefault("overdueInvoicesCount", 0L);
            long pendingApproval = (Long) ctx.getOrDefault("pendingApprovalCount", 0L);

            answer.append("### 💼 Financial Intelligence Briefing\n\n");
            answer.append(String.format("Here is the latest snapshot for **your organization**:\n\n"));
            answer.append(String.format("- **6-Month Total Spend**: **%s**\n", formatMoney(totalSpend)));
            answer.append(String.format("- **Outstanding Obligations**: **%s**\n", formatMoney(outstanding)));
            answer.append(String.format("- **Pending Approvals**: **%d invoice(s)**\n", pendingApproval));
            answer.append(String.format("- **Overdue Invoices**: **%d invoice(s)**\n\n", overdueCount));
            answer.append("You can ask me specific questions like: *'Which vendors have the highest spend?'*, *'Are any budgets exceeded?'*, or *'What is our 30-day cash outflow?'*");
        }

        return new ChatResponse(
            answer.toString(),
            intent,
            generateFollowUpSuggestions(query),
            ctx,
            Instant.now()
        );
    }

    private List<String> generateFollowUpSuggestions(String query) {
        return List.of(
            "Which vendors have the highest spend?",
            "Are any budgets exceeded this month?",
            "What is our cash outflow for the next 30 days?",
            "Show overdue invoices"
        );
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "₹0.00";
        return "₹" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String resolveEndpoint() {
        String base = aiProperties.getBaseUrl();
        if (base != null && !base.isBlank()) {
            return base.endsWith("/chat/completions") ? base : base + (base.endsWith("/") ? "chat/completions" : "/chat/completions");
        }
        if ("gemini".equalsIgnoreCase(aiProperties.getProvider())) {
            return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
        }
        if ("ollama".equalsIgnoreCase(aiProperties.getProvider())) {
            return "http://localhost:11434/v1/chat/completions";
        }
        return "https://api.openai.com/v1/chat/completions";
    }
}
