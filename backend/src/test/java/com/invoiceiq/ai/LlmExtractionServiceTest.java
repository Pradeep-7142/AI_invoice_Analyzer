package com.invoiceiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class LlmExtractionServiceTest {

    private AiProperties aiProperties;
    private MockAiExtractionService mockService;
    private ObjectMapper objectMapper;
    private LlmExtractionService llmService;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        mockService = new MockAiExtractionService();
        objectMapper = new ObjectMapper();
        llmService = new LlmExtractionService(aiProperties, mockService, objectMapper, RestClient.builder());
    }

    @Test
    void whenLlmDisabled_delegatesToMockExtraction() {
        aiProperties.setProvider("mock");
        aiProperties.setApiKey("");

        String text = "Tax Invoice\nInvoice No: INV-9901\nTotal: 4500.00\n";
        ExtractedInvoiceFields fields = llmService.extract(text);

        assertNotNull(fields);
        assertEquals("INV-9901", fields.invoiceNumber());
        assertEquals(new BigDecimal("4500.00"), fields.totalAmount());
    }

    @Test
    void whenTextIsBlank_returnsEmptyOrMockResult() {
        ExtractedInvoiceFields fields = llmService.extract("   ");
        assertNotNull(fields);
        assertNull(fields.invoiceNumber());
    }
}
