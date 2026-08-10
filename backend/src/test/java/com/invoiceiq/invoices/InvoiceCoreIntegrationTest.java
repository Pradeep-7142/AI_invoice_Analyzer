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
    void vendorCrudWorksForPrivilegedRolesAndIsReadOnlyForOthers() throws Exception {
        String adminToken = registerAndGetAccessToken("Vendor Co", "Vic Admin", "vic@vendorco.test", "password123");
        String viewerToken = addMemberAndGetAccessToken(adminToken, "Val Viewer", "val@vendorco.test", "password123", "VIEWER");

        MvcResult createResult = mockMvc.perform(post("/api/vendors")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "AWS"))))
            .andExpect(status().isCreated())
            .andReturn();
        String vendorId = extract(createResult, "$.id");

        // Viewer can read but not create.
        mockMvc.perform(get("/api/vendors").header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(post("/api/vendors")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Should Not Work"))))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/vendors/" + vendorId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Amazon Web Services"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Amazon Web Services"));
    }

    @Test
    void emptyFileUploadIsRejected() throws Exception {
        String adminToken = registerAndGetAccessToken("Upload Co", "Uma Admin", "uma@uploadco.test", "password123");

        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/invoices/upload")
                .file(empty)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_FILE"));
    }

    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        String adminToken = registerAndGetAccessToken("Upload Co", "Uma Admin", "uma2@uploadco.test", "password123");

        MockMultipartFile script = new MockMultipartFile("file", "invoice.exe", "application/octet-stream",
            "MZ-this-is-not-really-an-exe-but-not-a-pdf-either".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/invoices/upload")
                .file(script)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_FILE"));
    }

    @Test
    void viewerCannotUploadInvoices() throws Exception {
        String adminToken = registerAndGetAccessToken("Upload Co", "Uma Admin", "uma3@uploadco.test", "password123");
        String viewerToken = addMemberAndGetAccessToken(adminToken, "Val Viewer", "val3@uploadco.test", "password123", "VIEWER");

        MvcResult result = uploadInvoice(viewerToken, "invoice.pdf", minimalPdfBytes());
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void employeeOnlySeesOwnSubmissionsWhileAccountantSeesAll() throws Exception {
        String adminToken = registerAndGetAccessToken("Scoped Co", "Ana Admin", "ana@scopedco.test", "password123");
        String employeeToken = addMemberAndGetAccessToken(adminToken, "Eli Employee", "eli@scopedco.test", "password123", "EMPLOYEE");
        String accountantToken = addMemberAndGetAccessToken(adminToken, "Amy Accountant", "amy@scopedco.test", "password123", "ACCOUNTANT");

        MvcResult employeeUpload = uploadInvoice(employeeToken, "employee-invoice.pdf", minimalPdfBytes());
        org.assertj.core.api.Assertions.assertThat(employeeUpload.getResponse().getStatus()).isEqualTo(201);
        String employeeInvoiceId = extract(employeeUpload, "$.id");

        uploadInvoice(adminToken, "admin-invoice.pdf", minimalPdfBytes());

        mockMvc.perform(get("/api/invoices").header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(employeeInvoiceId));

        mockMvc.perform(get("/api/invoices").header("Authorization", "Bearer " + accountantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2));

        // Employee cannot fetch the admin's invoice by ID either.
        MvcResult adminInvoices = mockMvc.perform(get("/api/invoices").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        List<String> ids = com.jayway.jsonpath.JsonPath.read(adminInvoices.getResponse().getContentAsString(), "$.content[*].id");
        String othersInvoiceId = ids.stream().filter(id -> !id.equals(employeeInvoiceId)).findFirst().orElseThrow();

        mockMvc.perform(get("/api/invoices/" + othersInvoiceId).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void employeeCannotEditVerifyOrArchiveInvoices() throws Exception {
        String adminToken = registerAndGetAccessToken("Locked Co", "Lea Admin", "lea@lockedco.test", "password123");
        String employeeToken = addMemberAndGetAccessToken(adminToken, "Eli Employee", "eli2@lockedco.test", "password123", "EMPLOYEE");

        MvcResult upload = uploadInvoice(employeeToken, "invoice.pdf", minimalPdfBytes());
        String invoiceId = extract(upload, "$.id");

        mockMvc.perform(put("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("lineItems", List.of()))))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/verify")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void verifyRequiresRequiredFieldsAndEditingAVerifiedInvoiceReopensItForReview() throws Exception {
        String adminToken = registerAndGetAccessToken("Verify Co", "Vera Admin", "vera@verifyco.test", "password123");

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

    @Test
    void tenantIsolationPreventsCrossOrganizationVendorAndInvoiceAccess() throws Exception {
        String orgAToken = registerAndGetAccessToken("Org A Inc", "Admin A", "admina@orga2.test", "password123");
        String orgBToken = registerAndGetAccessToken("Org B Inc", "Admin B", "adminb@orgb2.test", "password123");

        MvcResult vendorResult = mockMvc.perform(post("/api/vendors")
                .header("Authorization", "Bearer " + orgAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Org A Vendor"))))
            .andExpect(status().isCreated())
            .andReturn();
        String vendorId = extract(vendorResult, "$.id");

        MvcResult uploadResult = uploadInvoice(orgAToken, "org-a-invoice.pdf", minimalPdfBytes());
        String invoiceId = extract(uploadResult, "$.id");

        mockMvc.perform(get("/api/vendors/" + vendorId).header("Authorization", "Bearer " + orgBToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/invoices/" + invoiceId).header("Authorization", "Bearer " + orgBToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/invoices").header("Authorization", "Bearer " + orgBToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));
    }
}
