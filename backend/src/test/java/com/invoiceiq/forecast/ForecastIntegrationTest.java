package com.invoiceiq.forecast;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoiceiq.AbstractIntegrationTest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ForecastIntegrationTest extends AbstractIntegrationTest {

    @Test
    void scheduledPaymentsAreBucketedIntoTheirWeekAndCountTowardTheScheduledTotal() throws Exception {
        String adminToken = registerAndGetAccessToken("Forecast Co", "Fia Admin", "fia@forecastco.test", "password123");
        String invoiceId = approvedInvoice(adminToken, "FC-1", "1000.00");

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "amount", 1000.00, "scheduledDate", today(), "method", "BANK_TRANSFER"))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/forecast/cash-flow").param("weeks", "4")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalScheduled").value(1000.00))
            .andExpect(jsonPath("$.weeks[0].scheduledAmount").value(1000.00));
    }

    @Test
    void approvedInvoiceWithNoScheduledPaymentCountsAsDueUnscheduled() throws Exception {
        String adminToken = registerAndGetAccessToken("Due Co", "Dua Admin", "dua@dueco.test", "password123");
        approvedInvoiceWithDueDate(adminToken, "FC-2", "750.00", plusDays(10));

        mockMvc.perform(get("/api/forecast/cash-flow").param("weeks", "4")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDueUnscheduled").value(750.00))
            .andExpect(jsonPath("$.totalScheduled").value(0));
    }

    @Test
    void aPastDueDateStillShowsUpInTheCurrentWeekBucketRatherThanBeingDropped() throws Exception {
        String adminToken = registerAndGetAccessToken("Overdue Forecast Co", "Ofa Admin", "ofa@overduefc.test", "password123");
        approvedInvoiceWithDueDate(adminToken, "FC-3", "400.00", "2020-01-01");

        mockMvc.perform(get("/api/forecast/cash-flow").param("weeks", "4")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDueUnscheduled").value(400.00))
            .andExpect(jsonPath("$.weeks[0].dueUnscheduledAmount").value(400.00));
    }

    @Test
    void schedulingTheFullOutstandingAmountRemovesItFromTheDueUnscheduledBucket() throws Exception {
        String adminToken = registerAndGetAccessToken("Reconcile Co", "Rec Admin", "rec@reconcileco.test", "password123");
        String invoiceId = approvedInvoiceWithDueDate(adminToken, "FC-4", "500.00", plusDays(5));

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "amount", 500.00, "scheduledDate", plusDays(4), "method", "CARD"))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/forecast/cash-flow").param("weeks", "4")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDueUnscheduled").value(0))
            .andExpect(jsonPath("$.totalScheduled").value(500.00));
    }

    @Test
    void monthlyProjectionAveragesActualSpendOverTheRequestedWindow() throws Exception {
        String adminToken = registerAndGetAccessToken("Projection Co", "Poa Admin", "poa@projectionco.test", "password123");
        String vendorId = createVendor(adminToken, "Projection Vendor");

        String invoice1 = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoice1, vendorId, "PROJ-1", today(), "600.00");
        String invoice2 = extract(uploadInvoice(adminToken, "b.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoice2, vendorId, "PROJ-2", today(), "600.00");

        mockMvc.perform(get("/api/forecast/monthly-projection").param("months", "2")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.averageMonthlySpend").value(600.00))
            .andExpect(jsonPath("$.basedOnMonths").value(2));
    }

    private String approvedInvoice(String adminToken, String invoiceNumber, String totalAmount) throws Exception {
        return approvedInvoiceWithDueDate(adminToken, invoiceNumber, totalAmount, null);
    }

    private String approvedInvoiceWithDueDate(String adminToken, String invoiceNumber, String totalAmount, String dueDate) throws Exception {
        String vendorId = createVendor(adminToken, "Vendor " + invoiceNumber);
        String invoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, invoiceNumber, today(), dueDate, totalAmount);
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/verify")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/submit-for-approval")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
        return invoiceId;
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String plusDays(int days) {
        return LocalDate.now().plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
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
        fillInvoice(token, invoiceId, vendorId, invoiceNumber, invoiceDate, null, totalAmount);
    }

    private void fillInvoice(String token, String invoiceId, String vendorId, String invoiceNumber, String invoiceDate, String dueDate, String totalAmount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("vendorId", vendorId);
        body.put("invoiceNumber", invoiceNumber);
        body.put("invoiceDate", invoiceDate);
        if (dueDate != null) {
            body.put("dueDate", dueDate);
        }
        body.put("totalAmount", Double.parseDouble(totalAmount));
        body.put("lineItems", List.of());

        mockMvc.perform(put("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());
    }
}
