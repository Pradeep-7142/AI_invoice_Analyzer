package com.invoiceiq.invoices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoiceiq.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class DocumentIntelligenceIntegrationTest extends AbstractIntegrationTest {

    @Test
    void extractsFieldsWithConfidenceFromAWellFormedInvoicePdf() throws Exception {
        String adminToken = registerAndGetAccessToken("Intel Co", "Ivy Admin", "ivy@intelco.test", "password123");

        byte[] pdf = pdfWithText(
            "Zoom Video Communications",
            "TAX INVOICE",
            "Invoice Number: INV-7788",
            "Invoice Date: 05/03/2026",
            "Due Date: 20/03/2026",
            "Subtotal: 12,000.00",
            "Tax: 2,160.00",
            "Grand Total: 14,160.00",
            "GSTIN: 29AABCU9603R1ZX"
        );

        MvcResult upload = uploadInvoice(adminToken, "invoice.pdf", pdf);
        String body = upload.getResponse().getContentAsString();

        assertThat(upload.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<String>read(body, "$.invoiceNumber")).isEqualTo("INV-7788");
        assertThat(((Number) JsonPath.read(body, "$.totalAmount")).doubleValue()).isEqualTo(14160.00);
        assertThat(((Number) JsonPath.read(body, "$.fieldConfidence.invoiceNumber")).doubleValue()).isGreaterThan(0.85);
        assertThat(JsonPath.<String>read(body, "$.documents[0].documentType")).isEqualTo("INVOICE");
        assertThat(JsonPath.<String>read(body, "$.documents[0].processingStatus")).isEqualTo("PROCESSED");
        // Vendor wasn't pre-created, so it can't resolve to a Vendor record yet, but the raw name is kept for the reviewer.
        assertThat(JsonPath.<String>read(body, "$.vendorNameRaw")).isEqualTo("Zoom Video Communications");
    }

    @Test
    void resolvesExtractedVendorNameToAnExistingVendorRecord() throws Exception {
        String adminToken = registerAndGetAccessToken("Resolve Co", "Rae Admin", "rae@resolveco.test", "password123");

        mockMvc.perform(post("/api/vendors")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Notion Labs Inc"))))
            .andExpect(status().isCreated());

        byte[] pdf = pdfWithText("Notion Labs Inc", "Invoice Number: INV-1", "Total: 100.00");
        MvcResult upload = uploadInvoice(adminToken, "invoice.pdf", pdf);

        assertThat(extract(upload, "$.vendor.name")).isEqualTo("Notion Labs Inc");
    }

    @Test
    void rejectsAPurchaseOrderWithAClearExplanationButStillPersistsTheRecord() throws Exception {
        String adminToken = registerAndGetAccessToken("Reject Co", "Rex Admin", "rex@rejectco.test", "password123");

        byte[] pdf = pdfWithText("PURCHASE ORDER", "PO Number: PO-991", "Ship To: Main Warehouse");
        MvcResult upload = uploadInvoice(adminToken, "po.pdf", pdf);
        String invoiceId = extract(upload, "$.id");

        mockMvc.perform(get("/api/invoices/" + invoiceId).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documents[0].processingStatus").value("REJECTED"))
            .andExpect(jsonPath("$.documents[0].rejectionReason").value(containsString("purchase order")))
            .andExpect(jsonPath("$.invoiceNumber").value(nullValue()));
    }

    @Test
    void rejectsAPasswordProtectedPdfWithAClearReason() throws Exception {
        String adminToken = registerAndGetAccessToken("Locked Doc Co", "Lyn Admin", "lyn@lockeddocco.test", "password123");

        MvcResult upload = uploadInvoice(adminToken, "locked.pdf", passwordProtectedPdfBytes());

        assertThat(upload.getResponse().getStatus()).isEqualTo(201);
        assertThat(extract(upload, "$.documents[0].processingStatus")).isEqualTo("REJECTED");
        assertThat(extract(upload, "$.documents[0].rejectionReason")).contains("password");
    }

    @Test
    void rejectsACorruptedPdfWithAClearReason() throws Exception {
        String adminToken = registerAndGetAccessToken("Corrupt Co", "Cor Admin", "cor@corruptco.test", "password123");

        MvcResult upload = uploadInvoice(adminToken, "broken.pdf", corruptedPdfBytes());

        assertThat(upload.getResponse().getStatus()).isEqualTo(201);
        assertThat(extract(upload, "$.documents[0].processingStatus")).isEqualTo("REJECTED");
        assertThat(extract(upload, "$.documents[0].rejectionReason")).contains("corrupted");
    }

    @Test
    void aBlankPdfWithNoTextIsNeedsReviewRatherThanRejected() throws Exception {
        String adminToken = registerAndGetAccessToken("Blank Co", "Bea Admin", "bea@blankco.test", "password123");

        MvcResult upload = uploadInvoice(adminToken, "blank.pdf", minimalPdfBytes());
        String body = upload.getResponse().getContentAsString();

        assertThat(upload.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<String>read(body, "$.documents[0].processingStatus")).isEqualTo("NEEDS_REVIEW");
        assertThat((Object) JsonPath.read(body, "$.invoiceNumber")).isNull();
    }
}
