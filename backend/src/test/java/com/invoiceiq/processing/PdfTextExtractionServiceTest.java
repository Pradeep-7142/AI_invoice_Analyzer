package com.invoiceiq.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfTextExtractionServiceTest {

    private final PdfTextExtractionService service = new PdfTextExtractionService();

    @Test
    void extractsSelectableTextFromARealPdf() throws IOException {
        byte[] pdf = pdfWithText("Invoice Number: INV-500", "Total: 999.00");

        PdfContent content = service.extractText(pdf);

        assertThat(content.text()).contains("Invoice Number: INV-500").contains("Total: 999.00");
        assertThat(content.pageCount()).isEqualTo(1);
    }

    @Test
    void rejectsAPasswordProtectedPdfWithoutAttemptingToBypassIt() throws IOException {
        byte[] pdf = passwordProtectedPdf();

        assertThatThrownBy(() -> service.extractText(pdf))
            .isInstanceOf(PasswordProtectedDocumentException.class)
            .hasMessageContaining("password");
    }

    @Test
    void rejectsATruncatedCorruptedPdf() throws IOException {
        byte[] valid = pdfWithText("hello");
        byte[] truncated = new byte[valid.length / 3];
        System.arraycopy(valid, 0, truncated, 0, truncated.length);

        assertThatThrownBy(() -> service.extractText(truncated))
            .isInstanceOf(CorruptedDocumentException.class)
            .hasMessageContaining("corrupted");
    }

    @Test
    void rendersFirstPageToAnImageForOcrFallback() throws IOException {
        byte[] pdf = pdfWithText("scanned-looking content");

        var image = service.renderFirstPage(pdf);

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThan(0);
        assertThat(image.getHeight()).isGreaterThan(0);
    }

    private byte[] pdfWithText(String... lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
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
        }
    }

    private byte[] passwordProtectedPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
