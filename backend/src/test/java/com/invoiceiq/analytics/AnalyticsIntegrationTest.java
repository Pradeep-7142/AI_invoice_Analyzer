package com.invoiceiq.analytics;

import static org.assertj.core.api.Assertions.assertThat;
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

class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void summaryTotalsSpendAndCountsStatusesAcrossTheOrganization() throws Exception {
        String adminToken = registerAndGetAccessToken("Summary Co", "Sam Admin", "sam@summaryco.test", "password123");
        String vendorId = createVendor(adminToken, "Vendor A", null);

        String invoiceId1 = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId1, vendorId, "SUM-1", today(), "1000.00");
        verify(adminToken, invoiceId1);

        String invoiceId2 = extract(uploadInvoice(adminToken, "b.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId2, vendorId, "SUM-2", today(), "2000.00");

        mockMvc.perform(get("/api/analytics/summary").param("months", "6")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpend").value(3000.00))
            .andExpect(jsonPath("$.invoiceCount").value(2))
            .andExpect(jsonPath("$.statusCounts.VERIFIED").value(1))
            .andExpect(jsonPath("$.statusCounts.NEEDS_REVIEW").value(1));
    }

    @Test
    void spendTrendBucketsInvoicesByTheirInvoiceDatesMonth() throws Exception {
        String adminToken = registerAndGetAccessToken("Trend Co", "Tia Admin", "tia@trendco.test", "password123");
        String vendorId = createVendor(adminToken, "Vendor B", null);

        String thisMonthInvoice = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, thisMonthInvoice, vendorId, "TREND-1", today(), "500.00");

        String lastMonthInvoice = extract(uploadInvoice(adminToken, "b.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, lastMonthInvoice, vendorId, "TREND-2", monthsAgo(1), "300.00");

        MvcResult result = mockMvc.perform(get("/api/analytics/spend-trend").param("months", "3")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

        List<Number> spends = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$[*].totalSpend");
        assertThat(spends).hasSize(3);
        assertThat(spends.get(2).doubleValue()).isEqualTo(500.00);
        assertThat(spends.get(1).doubleValue()).isEqualTo(300.00);
        assertThat(spends.get(0).doubleValue()).isEqualTo(0.00);
    }

    @Test
    void topVendorsAreSortedBySpendDescending() throws Exception {
        String adminToken = registerAndGetAccessToken("TopVendor Co", "Tav Admin", "tav@topvendorco.test", "password123");
        String bigVendorId = createVendor(adminToken, "Big Spender", null);
        String smallVendorId = createVendor(adminToken, "Small Spender", null);

        String bigInvoice = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, bigInvoice, bigVendorId, "BIG-1", today(), "9000.00");

        String smallInvoice = extract(uploadInvoice(adminToken, "b.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, smallInvoice, smallVendorId, "SMALL-1", today(), "100.00");

        mockMvc.perform(get("/api/analytics/vendors/top").param("months", "3")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].vendorName").value("Big Spender"))
            .andExpect(jsonPath("$[0].totalSpend").value(9000.00))
            .andExpect(jsonPath("$[1].vendorName").value("Small Spender"));
    }

    @Test
    void categorySpendIncludesTheBudgetLimitWhenABudgetExistsForThatCategory() throws Exception {
        String adminToken = registerAndGetAccessToken("Category Co", "Cat Admin", "cat@categoryco.test", "password123");
        String vendorId = createVendor(adminToken, "Cloud Vendor", "Cloud");

        mockMvc.perform(post("/api/budgets")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("category", "Cloud", "monthlyLimit", 5000.00))))
            .andExpect(status().isCreated());

        String invoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, "CAT-1", today(), "1200.00");

        mockMvc.perform(get("/api/analytics/categories").param("months", "3")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").value("Cloud"))
            .andExpect(jsonPath("$[0].totalSpend").value(1200.00))
            .andExpect(jsonPath("$[0].budgetLimit").value(5000.00));
    }

    @Test
    void budgetHistoryReturnsPerMonthActualSpendForTheRequestedWindow() throws Exception {
        String adminToken = registerAndGetAccessToken("History Co", "His Admin", "his@historyco.test", "password123");
        String vendorId = createVendor(adminToken, "History Vendor", "Travel");

        mockMvc.perform(post("/api/budgets")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("category", "Travel", "monthlyLimit", 1000.00))))
            .andExpect(status().isCreated());

        String thisMonthInvoice = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, thisMonthInvoice, vendorId, "HIST-1", today(), "1500.00");

        mockMvc.perform(get("/api/budgets/history").param("months", "3")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").value("Travel"))
            .andExpect(jsonPath("$[0].points", org.hamcrest.Matchers.hasSize(3)))
            .andExpect(jsonPath("$[0].points[2].actualSpend").value(1500.00))
            .andExpect(jsonPath("$[0].points[2].overBudget").value(true))
            .andExpect(jsonPath("$[0].points[0].actualSpend").value(0));
    }

    @Test
    void employeesCannotAccessOrganizationWideAnalytics() throws Exception {
        String adminToken = registerAndGetAccessToken("Locked Co", "Lia Admin", "lia@lockedco.test", "password123");
        String employeeToken = addMemberAndGetAccessToken(adminToken, "Emp Loyee", "emp@lockedco.test", "password123", "EMPLOYEE");

        mockMvc.perform(get("/api/analytics/summary")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String monthsAgo(int months) {
        return LocalDate.now().minusMonths(months).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String createVendor(String token, String name, String category) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (category != null) {
            body.put("category", category);
        }
        MvcResult result = mockMvc.perform(post("/api/vendors")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
        return extract(result, "$.id");
    }

    private void verify(String token, String invoiceId) throws Exception {
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/verify")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    private void fillInvoice(String token, String invoiceId, String vendorId, String invoiceNumber, String invoiceDate, String totalAmount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("vendorId", vendorId);
        body.put("invoiceNumber", invoiceNumber);
        body.put("invoiceDate", invoiceDate);
        body.put("totalAmount", Double.parseDouble(totalAmount));
        body.put("lineItems", List.of());

        mockMvc.perform(put("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());
    }
}
