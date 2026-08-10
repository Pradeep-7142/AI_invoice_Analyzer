package com.invoiceiq.dashboard;

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

class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Test
    void financeManagerSeesInvoicesPendingApprovalButNeverTheirOwnSubmission() throws Exception {
        String adminToken = registerAndGetAccessToken("Dash Co", "Dax Admin", "dax@dashco.test", "password123");
        setManagerThreshold(adminToken, "1000.00");
        String fmToken = addMemberAndGetAccessToken(adminToken, "Fin Manager", "fm@dashco.test", "password123", "FINANCE_MANAGER");

        String vendorId = createVendor(adminToken, "Vendor One");
        String submittedByAdmin = approvalPendingInvoice(adminToken, vendorId, "DASH-1", "5000.00");
        String submittedByManager = approvalPendingInvoice(fmToken, vendorId, "DASH-2", "6000.00");

        MvcResult result = mockMvc.perform(get("/api/dashboard/action-center")
                .header("Authorization", "Bearer " + fmToken))
            .andExpect(status().isOk())
            .andReturn();

        List<String> ids = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.pendingMyApproval[*].id");
        org.assertj.core.api.Assertions.assertThat(ids).contains(submittedByAdmin);
        org.assertj.core.api.Assertions.assertThat(ids).doesNotContain(submittedByManager);
    }

    @Test
    void submitterSeesTheirOwnRejectedInvoiceInNeedsMyAttention() throws Exception {
        String adminToken = registerAndGetAccessToken("Reject Dash Co", "Rda Admin", "rda@rejectdash.test", "password123");
        setManagerThreshold(adminToken, "1000.00");
        String accToken = addMemberAndGetAccessToken(adminToken, "Ann Accountant", "ann@rejectdash.test", "password123", "ACCOUNTANT");

        String vendorId = createVendor(accToken, "Vendor Two");
        String invoiceId = approvalPendingInvoice(accToken, vendorId, "DASH-3", "5000.00");

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Needs a PO number."))))
            .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/dashboard/action-center")
                .header("Authorization", "Bearer " + accToken))
            .andExpect(status().isOk())
            .andReturn();

        List<String> ids = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.needsMyAttention[*].id");
        org.assertj.core.api.Assertions.assertThat(ids).contains(invoiceId);
    }

    @Test
    void employeeOnlySeesTheirOwnOverdueInvoicesNotTheWholeOrganization() throws Exception {
        String adminToken = registerAndGetAccessToken("Overdue Dash Co", "Oda Admin", "oda@overduedash.test", "password123");
        String employeeToken = addMemberAndGetAccessToken(adminToken, "Emp Loyee", "emp@overduedash.test", "password123", "EMPLOYEE");

        mockMvc.perform(get("/api/dashboard/action-center")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingMyApproval").isEmpty())
            .andExpect(jsonPath("$.overdueInvoices").isEmpty());
    }

    @Test
    void overBudgetCategoriesShowUpOnTheActionCenter() throws Exception {
        String adminToken = registerAndGetAccessToken("Budget Dash Co", "Bda Admin", "bda@budgetdash.test", "password123");
        String vendorId = createVendor(adminToken, "Cloud Vendor", "Cloud");

        mockMvc.perform(post("/api/budgets")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("category", "Cloud", "monthlyLimit", 100.00))))
            .andExpect(status().isCreated());

        String invoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, "DASH-4", today(), "500.00");

        mockMvc.perform(get("/api/dashboard/action-center")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overBudgetCategories[0].category").value("Cloud"));
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private void setManagerThreshold(String adminToken, String threshold) throws Exception {
        mockMvc.perform(put("/api/organizations/finance-settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("managerApprovalThreshold", Double.parseDouble(threshold)))))
            .andExpect(status().isOk());
    }

    private String createVendor(String token, String name) throws Exception {
        return createVendor(token, name, null);
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

    /** Uploads, fills, verifies, and submits for approval under the given token, returning the invoice id. */
    private String approvalPendingInvoice(String token, String vendorId, String invoiceNumber, String totalAmount) throws Exception {
        String invoiceId = extract(uploadInvoice(token, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(token, invoiceId, vendorId, invoiceNumber, today(), totalAmount);
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/verify")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/submit-for-approval")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        return invoiceId;
    }
}
