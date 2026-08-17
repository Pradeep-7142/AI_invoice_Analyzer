package com.invoiceiq.invoices;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoiceiq.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class InvoiceCoreIntegrationTest extends AbstractIntegrationTest {

    @Test
    void vendorCrudWorksForAdmin() throws Exception {
        String adminToken = registerAndGetAccessToken("Vic Admin", "vic@vendorco.test", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/vendors")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "AWS"))))
            .andExpect(status().isCreated())
            .andReturn();
        String vendorId = extract(createResult, "$.id");

        mockMvc.perform(get("/api/vendors").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(put("/api/vendors/" + vendorId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Amazon Web Services"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Amazon Web Services"));
    }

    @Test
    void emptyFileUploadIsRejected() throws Exception {
        String adminToken = registerAndGetAccessToken("Uma Admin", "uma@uploadco.test", "password123");

        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/invoices/upload")
                .file(empty)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_FILE"));
    }

    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        String adminToken = registerAndGetAccessToken("Uma Admin", "uma2@uploadco.test", "password123");

        MockMultipartFile script = new MockMultipartFile("file", "invoice.exe", "application/octet-stream",
            "MZ-this-is-not-really-an-exe-but-not-a-pdf-either".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/invoices/upload")
                .file(script)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_FILE"));
    }

    @Test
    void invoiceLifecycleWorkflowOperatesCorrectly() throws Exception {
        String adminToken = registerAndGetAccessToken("Vera Admin", "vera@verifyco.test", "password123");

        MvcResult vendorResult = mockMvc.perform(post("/api/vendors")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Adobe"))))
            .andExpect(status().isCreated())
            .andReturn();
        String vendorId = extract(vendorResult, "$.id");

        MvcResult upload = uploadInvoice(adminToken, "invoice.pdf", minimalPdfBytes());
        String invoiceId = extract(upload, "$.id");
        org.assertj.core.api.Assertions.assertThat(extract(upload, "$.status")).isEqualTo("NEEDS_REVIEW");

        // Missing invoice number / date / total => verification is rejected.
        mockMvc.perform(post("/api/invoices/" + invoiceId + "/verify")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "vendorId", vendorId,
                    "invoiceNumber", "INV-1001",
                    "invoiceDate", "2026-01-15",
                    "totalAmount", 1200.00,
                    "lineItems", List.of(Map.of(
                        "description", "Adobe Creative Cloud",
                        "quantity", 1,
                        "unitPrice", 1200.00,
                        "totalAmount", 1200.00
                    ))
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lineItems.length()").value(1));

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/verify")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VERIFIED"));

        // Editing a verified invoice reopens it for review.
        mockMvc.perform(put("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "vendorId", vendorId,
                    "invoiceNumber", "INV-1001-CORRECTED",
                    "invoiceDate", "2026-01-15",
                    "totalAmount", 1300.00,
                    "lineItems", List.of()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/archive")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // Archived invoices are frozen.
        mockMvc.perform(put("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("lineItems", List.of()))))
            .andExpect(status().isUnprocessableEntity());
    }
}
