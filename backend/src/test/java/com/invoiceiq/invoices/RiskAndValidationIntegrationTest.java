package com.invoiceiq.invoices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoiceiq.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RiskAndValidationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void verifyIsBlockedWithAllMissingFieldsListedWhenValidationFails() throws Exception {
        String adminToken = registerAndGetAccessToken("Risk Co", "Rob Admin", "rob@riskco.test", "password123");

        MvcResult upload = uploadInvoice(adminToken, "blank.pdf", minimalPdfBytes());
        String invoiceId = extract(upload, "$.id");

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/verify")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("Vendor"),
                org.hamcrest.Matchers.containsString("Invoice number"))));
    }

    @Test
    void invoiceDetailIncludesFullPassingValidationChecklistOnceFilledIn() throws Exception {
        String adminToken = registerAndGetAccessToken("Checklist Co", "Cal Admin", "cal@checklistco.test", "password123");

        String vendorId = createVendor(adminToken, "Adobe");
        String invoiceId = extract(uploadInvoice(adminToken, "invoice.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, "INV-1", "2026-03-01", "100.00");

        MvcResult result = mockMvc.perform(get("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

        List<String> statuses = JsonPath.read(result.getResponse().getContentAsString(), "$.validationResults[*].status");
        assertThat(statuses).isNotEmpty();
        assertThat(statuses).doesNotContain("ERROR");
    }

    @Test
    void exactDuplicateInvoiceNumberIsFlaggedAsADuplicateWarningWithHighProbability() throws Exception {
        String adminToken = registerAndGetAccessToken("Dup Co", "Dee Admin", "dee@dupco.test", "password123");
        String vendorId = createVendor(adminToken, "Acme Supplies");

        String firstInvoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, firstInvoiceId, vendorId, "INV-DUP-1", "2026-03-01", "1000.00");

        String secondInvoiceId = extract(uploadInvoice(adminToken, "b.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, secondInvoiceId, vendorId, "INV-DUP-1", "2026-03-05", "999.00");

        MvcResult result = mockMvc.perform(get("/api/invoices/" + secondInvoiceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.duplicateWarnings[0].probability").value(0.98))
            .andExpect(jsonPath("$.riskScore").value(org.hamcrest.Matchers.greaterThan(0)))
            .andReturn();

        String reason = JsonPath.read(result.getResponse().getContentAsString(), "$.duplicateWarnings[0].reason");
        assertThat(reason).contains("Exact match");
    }

    @Test
    void invoiceFarAboveVendorHistoricalAverageIsFlaggedAsAnAnomaly() throws Exception {
        String adminToken = registerAndGetAccessToken("Anomaly Co", "Amy Admin", "amy@anomalyco.test", "password123");
        String vendorId = createVendor(adminToken, "AWS");

        for (int i = 0; i < 4; i++) {
            String invoiceId = extract(uploadInvoice(adminToken, "hist" + i + ".pdf", minimalPdfBytes()), "$.id");
            fillInvoice(adminToken, invoiceId, vendorId, "AWS-" + i, "2026-0" + (i + 1) + "-01", "50000.00");
        }

        String spikeInvoiceId = extract(uploadInvoice(adminToken, "spike.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, spikeInvoiceId, vendorId, "AWS-SPIKE", "2026-05-01", "500000.00");

        mockMvc.perform(get("/api/invoices/" + spikeInvoiceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.anomaly.severity").value("HIGH"))
            .andExpect(jsonPath("$.riskScore").value(org.hamcrest.Matchers.greaterThanOrEqualTo(30)));
    }

    private String createVendor(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vendors")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isCreated())
            .andReturn();
        return extract(result, "$.id");
    }

    private void fillInvoice(String token, String invoiceId, String vendorId, String invoiceNumber, String invoiceDate, String totalAmount) throws Exception {
        mockMvc.perform(put("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "vendorId", vendorId,
                    "invoiceNumber", invoiceNumber,
                    "invoiceDate", invoiceDate,
                    "totalAmount", Double.parseDouble(totalAmount),
                    "lineItems", List.of()
                ))))
            .andExpect(status().isOk());
    }
}
