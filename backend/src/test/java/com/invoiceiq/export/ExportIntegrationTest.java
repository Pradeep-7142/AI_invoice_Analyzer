package com.invoiceiq.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

class ExportIntegrationTest extends AbstractIntegrationTest {

    @Test
    void invoiceCsvExportIncludesEveryOrganizationInvoiceForAManager() throws Exception {
        String adminToken = registerAndGetAccessToken("Export Co", "Exa Admin", "exa@exportco.test", "password123");
        String vendorId = createVendor(adminToken, "Export Vendor");
        String invoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, "EXP-1", today(), "1234.50");

        MvcResult result = mockMvc.perform(get("/api/invoices/export.csv")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"invoices.csv\""))
            .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(csv).startsWith("Invoice Number,Vendor,Invoice Date,Due Date,Currency,Total Amount,Status,Submitted By,Created At");
        assertThat(csv).contains("EXP-1", "Export Vendor", "1234.50");
    }

    @Test
    void invoiceCsvExportIsScopedToOwnSubmissionsForAnEmployee() throws Exception {
        String adminToken = registerAndGetAccessToken("Scoped Export Co", "Sea Admin", "sea@scopedexport.test", "password123");
        String employeeToken = addMemberAndGetAccessToken(adminToken, "Emp Loyee", "emp@scopedexport.test", "password123", "EMPLOYEE");
        String vendorId = createVendor(adminToken, "Scoped Vendor");

        String adminInvoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, adminInvoiceId, vendorId, "ADMIN-EXP", today(), "100.00");

        uploadInvoice(employeeToken, "b.pdf", minimalPdfBytes());

        MvcResult result = mockMvc.perform(get("/api/invoices/export.csv")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(csv).doesNotContain("ADMIN-EXP");
    }

    @Test
    void paymentCsvExportIncludesScheduledPayments() throws Exception {
        String adminToken = registerAndGetAccessToken("Payment Export Co", "Pea Admin", "pea@paymentexport.test", "password123");
        String vendorId = createVendor(adminToken, "Payment Vendor");
        String invoiceId = extract(uploadInvoice(adminToken, "a.pdf", minimalPdfBytes()), "$.id");
        fillInvoice(adminToken, invoiceId, vendorId, "PAY-EXP-1", today(), "2000.00");
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/verify").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/submit-for-approval").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "amount", 2000.00, "scheduledDate", today(), "method", "BANK_TRANSFER"))))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/payments/export.csv")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"payments.csv\""))
            .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(csv).contains("PAY-EXP-1", "2000.00", "SCHEDULED", "BANK_TRANSFER");
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
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
