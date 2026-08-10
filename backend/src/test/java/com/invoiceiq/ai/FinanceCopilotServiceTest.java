package com.invoiceiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.ai.copilot.FinanceCopilotService;
import com.invoiceiq.analytics.AnalyticsService;
import com.invoiceiq.dto.AnalyticsSummaryResponse;
import com.invoiceiq.dto.CashFlowForecastResponse;
import com.invoiceiq.dto.CopilotDto.ChatRequest;
import com.invoiceiq.dto.CopilotDto.ChatResponse;
import com.invoiceiq.dto.CopilotDto.QuickInsight;
import com.invoiceiq.forecast.ForecastService;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.security.CurrentUser;
import com.invoiceiq.service.BudgetService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class FinanceCopilotServiceTest {

    private AnalyticsService analyticsService;
    private BudgetService budgetService;
    private ForecastService forecastService;
    private InvoiceRepository invoiceRepository;
    private CurrentUser currentUser;
    private AiProperties aiProperties;
    private FinanceCopilotService copilotService;

    @BeforeEach
    void setUp() {
        analyticsService = Mockito.mock(AnalyticsService.class);
        budgetService = Mockito.mock(BudgetService.class);
        forecastService = Mockito.mock(ForecastService.class);
        invoiceRepository = Mockito.mock(InvoiceRepository.class);
        currentUser = Mockito.mock(CurrentUser.class);
        aiProperties = new AiProperties();
        aiProperties.setProvider("mock");

        UUID orgId = UUID.randomUUID();
        when(currentUser.organizationId()).thenReturn(orgId);
        when(invoiceRepository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(analyticsService.summary(anyInt())).thenReturn(
            new AnalyticsSummaryResponse(6, new BigDecimal("120000.00"), 5, new BigDecimal("24000.00"), new BigDecimal("35000.00"), Map.of())
        );
        when(forecastService.cashFlow(anyInt())).thenReturn(
            new CashFlowForecastResponse(List.of(), new BigDecimal("15000.00"), new BigDecimal("20000.00"))
        );

        copilotService = new FinanceCopilotService(
            analyticsService, budgetService, forecastService, invoiceRepository,
            currentUser, aiProperties, RestClient.builder(), new ObjectMapper()
        );
    }

    @Test
    void answersSpendQuestionsGroundedInData() {
        ChatResponse response = copilotService.chat(new ChatRequest("How much did we spend in total?", List.of()));
        assertNotNull(response);
        assertTrue(response.answer().contains("120000"));
        assertEquals("SPEND_BREAKDOWN", response.intent());
    }

    @Test
    void generatesQuickInsights() {
        List<QuickInsight> insights = copilotService.getQuickInsights();
        assertNotNull(insights);
        assertFalse(insights.isEmpty());
    }
}
