package com.invoiceiq.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoiceiq.AbstractIntegrationTest;
import com.invoiceiq.service.OverdueInvoiceJob;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class FinanceWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OverdueInvoiceJob overdueInvoiceJob;

    @Test
    void submittingAnInvoiceBelowEveryThresholdAutoApprovesIt() throws Exception {
        String adminToken = registerAndGetAccessToken("NoThreshold Co", "Nia Admin", "nia@nothreshold.test", "password123");
        String vendorId = createVendor(adminToken, "Office Depot", null);
        String invoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, "INV-1", "2026-03-01", null, "500.00");
        verify(adminToken, invoiceId);

        submitForApproval(adminToken, invoiceId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void invoiceAboveManagerThresholdNeedsApprovalAndCannotBeApprovedByItsOwnSubmitter() throws Exception {
        String adminToken = registerAndGetAccessToken("Threshold Co", "Tia Admin", "tia@thresholdco.test", "password123");
        setFinanceSettings(adminToken, "10000.00", null).andExpect(status().isOk());
        String fmToken = addMemberAndGetAccessToken(adminToken, "Fin Manager", "fm@thresholdco.test", "password123", "FINANCE_MANAGER");

        String vendorId = createVendor(fmToken, "AWS", null);
        String invoiceId = extract(uploadInvoice(fmToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(fmToken, invoiceId, vendorId, "INV-2", "2026-03-01", null, "50000.00");
        verify(fmToken, invoiceId);

        submitForApproval(fmToken, invoiceId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.requiredApprovalRole").value("FINANCE_MANAGER"));

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/approve")
                .header("Authorization", "Bearer " + fmToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("submitted yourself")));

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.approvalHistory[0].decision").value("APPROVED"))
            .andExpect(jsonPath("$.approvalHistory[0].requiredRole").value("FINANCE_MANAGER"));
    }

    @Test
    void invoiceAboveAdminThresholdCannotBeApprovedByAFinanceManager() throws Exception {
        String adminToken = registerAndGetAccessToken("BigTicket Co", "Bea Admin", "bea@bigticket.test", "password123");
        setFinanceSettings(adminToken, "10000.00", "100000.00").andExpect(status().isOk());
        String accToken = addMemberAndGetAccessToken(adminToken, "Ann Accountant", "ann@bigticket.test", "password123", "ACCOUNTANT");
        String fmToken = addMemberAndGetAccessToken(adminToken, "Fin Manager", "fm@bigticket.test", "password123", "FINANCE_MANAGER");

        String vendorId = createVendor(accToken, "Big Vendor", null);
        String invoiceId = extract(uploadInvoice(accToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(accToken, invoiceId, vendorId, "INV-3", "2026-03-01", null, "200000.00");
        verify(accToken, invoiceId);

        submitForApproval(accToken, invoiceId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.requiredApprovalRole").value("ORGANIZATION_ADMIN"));

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/approve")
                .header("Authorization", "Bearer " + fmToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("organization admin")));

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.approvalHistory[0].requiredRole").value("ORGANIZATION_ADMIN"));
    }

    @Test
    void rejectingAPendingApprovalInvoiceSendsItBackToNeedsReviewWithTheReasonRecorded() throws Exception {
        String adminToken = registerAndGetAccessToken("Reject Co", "Rae Admin", "rae@rejectco.test", "password123");
        setFinanceSettings(adminToken, "1000.00", null).andExpect(status().isOk());
        String accToken = addMemberAndGetAccessToken(adminToken, "Ann Accountant", "ann@rejectco.test", "password123", "ACCOUNTANT");

        String vendorId = createVendor(accToken, "Some Vendor", null);
        String invoiceId = extract(uploadInvoice(accToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(accToken, invoiceId, vendorId, "INV-4", "2026-03-01", null, "5000.00");
        verify(accToken, invoiceId);
        submitForApproval(accToken, invoiceId).andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Missing purchase order reference."))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$.approvalHistory[0].decision").value("REJECTED"))
            .andExpect(jsonPath("$.approvalHistory[0].reason").value("Missing purchase order reference."));
    }

    @Test
    void schedulingThenCompletingAFullPaymentMarksTheInvoicePaid() throws Exception {
        String adminToken = registerAndGetAccessToken("Payer Co", "Pia Admin", "pia@payerco.test", "password123");
        String invoiceId = approvedInvoice(adminToken, "PAY-1", "1000.00");

        MvcResult scheduled = schedulePayment(adminToken, invoiceId, "1000.00", "2026-08-15", "BANK_TRANSFER")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PAYMENT_SCHEDULED"))
            .andExpect(jsonPath("$.payments[0].status").value("SCHEDULED"))
            .andReturn();
        String paymentId = extract(scheduled, "$.payments[0].id");

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments/" + paymentId + "/complete")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.paidAmount").value(1000.00))
            .andExpect(jsonPath("$.outstandingAmount").value(0));
    }

    @Test
    void aPartialPaymentLeavesTheInvoicePartiallyPaidUntilTheRemainderIsPaid() throws Exception {
        String adminToken = registerAndGetAccessToken("Partial Co", "Pat Admin", "pat@partialco.test", "password123");
        String invoiceId = approvedInvoice(adminToken, "PAY-2", "1000.00");

        MvcResult firstScheduled = schedulePayment(adminToken, invoiceId, "400.00", "2026-08-15", "BANK_TRANSFER")
            .andExpect(status().isCreated())
            .andReturn();
        String firstPaymentId = extract(firstScheduled, "$.payments[0].id");
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments/" + firstPaymentId + "/complete")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
            .andExpect(jsonPath("$.outstandingAmount").value(600.00));

        MvcResult secondScheduled = schedulePayment(adminToken, invoiceId, "600.00", "2026-08-20", "CHEQUE")
            .andExpect(status().isCreated())
            .andReturn();
        String secondPaymentId = extract(secondScheduled, "$.payments[1].id");
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments/" + secondPaymentId + "/complete")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.paidAmount").value(1000.00));
    }

    @Test
    void schedulingAPaymentAboveTheOutstandingBalanceIsRejected() throws Exception {
        String adminToken = registerAndGetAccessToken("Overpay Co", "Ova Admin", "ova@overpayco.test", "password123");
        String invoiceId = approvedInvoice(adminToken, "PAY-3", "1000.00");

        schedulePayment(adminToken, invoiceId, "1500.00", "2026-08-15", "BANK_TRANSFER")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceeds the outstanding balance")));
    }

    @Test
    void cancellingAScheduledPaymentReturnsTheInvoiceToApproved() throws Exception {
        String adminToken = registerAndGetAccessToken("Cancel Co", "Cal Admin", "cal@cancelco.test", "password123");
        String invoiceId = approvedInvoice(adminToken, "PAY-4", "1000.00");

        MvcResult scheduled = schedulePayment(adminToken, invoiceId, "1000.00", "2026-08-15", "CASH")
            .andExpect(status().isCreated())
            .andReturn();
        String paymentId = extract(scheduled, "$.payments[0].id");

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments/" + paymentId + "/cancel")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.payments[0].status").value("CANCELLED"));
    }

    @Test
    void disputingAnApprovedInvoiceAndResolvingItRoundTrips() throws Exception {
        String adminToken = registerAndGetAccessToken("Dispute Co", "Dee Admin", "dee@disputeco.test", "password123");
        String invoiceId = approvedInvoice(adminToken, "PAY-5", "1000.00");

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/dispute")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Goods never received."))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISPUTED"))
            .andExpect(jsonPath("$.disputeReason").value("Goods never received."));

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/resolve-dispute")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$.disputeReason").doesNotExist());
    }

    @Test
    void budgetStatusReflectsActualInvoiceSpendAndFlagsOverBudget() throws Exception {
        String adminToken = registerAndGetAccessToken("Budget Co", "Baz Admin", "baz@budgetco.test", "password123");
        String vendorId = createVendor(adminToken, "Cloud Vendor", "Cloud");

        mockMvc.perform(post("/api/budgets")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("category", "Cloud", "monthlyLimit", 100000.00))))
            .andExpect(status().isCreated());

        String invoice1 = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoice1, vendorId, "CLOUD-1", "2026-08-05", null, "60000.00");

        mockMvc.perform(get("/api/budgets/status")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].actualSpend").value(60000.00))
            .andExpect(jsonPath("$[0].overBudget").value(false));

        String invoice2 = extract(uploadInvoice(adminToken, "b.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoice2, vendorId, "CLOUD-2", "2026-08-06", null, "50000.00");

        mockMvc.perform(get("/api/budgets/status")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].actualSpend").value(110000.00))
            .andExpect(jsonPath("$[0].overBudget").value(true));
    }

    @Test
    void financeSettingsCanOnlyBeChangedByAnAdminAndRejectAnInvertedThreshold() throws Exception {
        String adminToken = registerAndGetAccessToken("Settings Co", "Sia Admin", "sia@settingsco.test", "password123");
        String employeeToken = addMemberAndGetAccessToken(adminToken, "Emp Loyee", "emp@settingsco.test", "password123", "EMPLOYEE");

        mockMvc.perform(put("/api/organizations/finance-settings")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("managerApprovalThreshold", 1000))))
            .andExpect(status().isForbidden());

        setFinanceSettings(adminToken, "50000.00", "10000.00")
            .andExpect(status().isUnprocessableEntity());

        setFinanceSettings(adminToken, "10000.00", "50000.00")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.managerApprovalThreshold").value(10000.00))
            .andExpect(jsonPath("$.adminApprovalThreshold").value(50000.00));

        mockMvc.perform(get("/api/organizations/finance-settings")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.managerApprovalThreshold").value(10000.00));
    }

    @Test
    void overdueSweepFlagsAnApprovedInvoicePastItsDueDateWithNoPayments() throws Exception {
        String adminToken = registerAndGetAccessToken("Overdue Co", "Ova Admin", "ova@overdueco.test", "password123");
        String vendorId = createVendor(adminToken, "Old Vendor", null);
        String invoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, "OLD-1", "2026-01-01", "2026-01-15", "1000.00");
        verify(adminToken, invoiceId);
        submitForApproval(adminToken, invoiceId).andExpect(status().isOk());

        overdueInvoiceJob.markOverdueInvoices();

        mockMvc.perform(get("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OVERDUE"));
    }

    private String approvedInvoice(String adminToken, String invoiceNumber, String totalAmount) throws Exception {
        String vendorId = createVendor(adminToken, "Vendor " + invoiceNumber, null);
        String invoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, invoiceNumber, "2026-03-01", null, totalAmount);
        verify(adminToken, invoiceId);
        submitForApproval(adminToken, invoiceId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        return invoiceId;
    }

    private String createVendor(String token, String name, String category) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
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

    private org.springframework.test.web.servlet.ResultActions submitForApproval(String token, String invoiceId) throws Exception {
        return mockMvc.perform(post("/api/invoices/" + invoiceId + "/submit-for-approval")
                .header("Authorization", "Bearer " + token));
    }

    private void fillInvoice(String token, String invoiceId, String vendorId, String invoiceNumber, String invoiceDate, String dueDate, String totalAmount) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
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

    private org.springframework.test.web.servlet.ResultActions setFinanceSettings(String adminToken, String managerThreshold, String adminThreshold) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        if (managerThreshold != null) {
            body.put("managerApprovalThreshold", Double.parseDouble(managerThreshold));
        }
        if (adminThreshold != null) {
            body.put("adminApprovalThreshold", Double.parseDouble(adminThreshold));
        }
        return mockMvc.perform(put("/api/organizations/finance-settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.ResultActions schedulePayment(String token, String invoiceId, String amount, String scheduledDate, String method) throws Exception {
        return mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "amount", Double.parseDouble(amount),
                    "scheduledDate", scheduledDate,
                    "method", method))));
    }
}
