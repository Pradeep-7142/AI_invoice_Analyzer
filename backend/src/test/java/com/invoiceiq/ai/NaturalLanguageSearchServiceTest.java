package com.invoiceiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.ai.search.NaturalLanguageSearchService;
import com.invoiceiq.dto.AiIntelligenceDto.NaturalSearchRequest;
import com.invoiceiq.dto.AiIntelligenceDto.NaturalSearchResponse;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class NaturalLanguageSearchServiceTest {

    private InvoiceRepository invoiceRepository;
    private CurrentUser currentUser;
    private AiProperties aiProperties;
    private NaturalLanguageSearchService searchService;

    @BeforeEach
    void setUp() {
        invoiceRepository = Mockito.mock(InvoiceRepository.class);
        currentUser = Mockito.mock(CurrentUser.class);
        aiProperties = new AiProperties();
        aiProperties.setProvider("mock");

        UUID orgId = UUID.randomUUID();
        when(currentUser.organizationId()).thenReturn(orgId);
        when(currentUser.role()).thenReturn(com.invoiceiq.entity.OrgRole.FINANCE_MANAGER);
        when(invoiceRepository.findByOrganizationId(orgId)).thenReturn(List.of());

        searchService = new NaturalLanguageSearchService(
            invoiceRepository, currentUser, aiProperties, RestClient.builder(), new ObjectMapper()
        );
    }

    @Test
    void parsesAmountAndStatusHeuristically() {
        NaturalSearchResponse response = searchService.search(new NaturalSearchRequest("Show overdue invoices over 50000"));
        assertNotNull(response);
        assertEquals(InvoiceStatus.OVERDUE, response.criteria().status());
        assertEquals(new BigDecimal("50000"), response.criteria().minAmount());
    }

    @Test
    void parsesCategoryAndApprovedStatus() {
        NaturalSearchResponse response = searchService.search(new NaturalSearchRequest("Approved cloud invoices under 25000"));
        assertNotNull(response);
        assertEquals(InvoiceStatus.APPROVED, response.criteria().status());
        assertEquals("Cloud", response.criteria().category());
        assertEquals(new BigDecimal("25000"), response.criteria().maxAmount());
    }
}
