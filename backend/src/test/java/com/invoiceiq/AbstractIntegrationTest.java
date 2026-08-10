package com.invoiceiq;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared MockMvc + real-embedded-Postgres harness, plus the auth helpers
 * every integration test needs (register/login/extract-a-field-from-JSON).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String registerAndGetAccessToken(String orgName, String fullName, String email, String password) throws Exception {
        return extract(register(orgName, fullName, email, password), "$.accessToken");
    }

    protected MvcResult register(String orgName, String fullName, String email, String password) throws Exception {
        Map<String, String> body = Map.of(
            "organizationName", orgName,
            "fullName", fullName,
            "email", email,
            "password", password
        );
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    protected String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
        return extract(result, "$.accessToken");
    }

    protected String extract(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path).toString();
    }

    protected String addMemberAndGetAccessToken(String adminToken, String fullName, String email, String password, String role) throws Exception {
        mockMvc.perform(post("/api/organizations/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "fullName", fullName, "email", email, "password", password, "role", role))))
            .andExpect(status().isCreated());
        return loginAndGetAccessToken(email, password);
    }

    protected MvcResult uploadInvoice(String token, String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", content);
        return mockMvc.perform(multipart("/api/invoices/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andReturn();
    }

    /** A genuinely well-formed, blank single-page PDF (built with PDFBox, not hand-rolled bytes). */
    protected byte[] minimalPdfBytes() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A well-formed PDF with real selectable text, one line per string, for exercising extraction. */
    protected byte[] pdfWithText(String... lines) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(50, 700);
                for (String line : lines) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -18);
                }
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A PDF that requires a password to open, so PDFBox's default (empty-password) load fails. */
    protected byte[] passwordProtectedPdfBytes() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Starts with a real PDF header (so Tika sniffs it as application/pdf) but the structure is truncated/broken. */
    protected byte[] corruptedPdfBytes() {
        byte[] valid = minimalPdfBytes();
        byte[] truncated = new byte[valid.length / 3];
        System.arraycopy(valid, 0, truncated, 0, truncated.length);
        return truncated;
    }
}
