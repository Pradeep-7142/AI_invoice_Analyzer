package com.invoiceiq.processing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

/**
 * Real (non-AI) PDF parsing via Apache PDFBox: selectable text, page count,
 * and — for image-only/scanned PDFs — rendering the first page to a bitmap
 * so it can be handed to OCR. Also where corrupted and password-protected
 * PDFs are actually detected (PDFBox throws distinctly for each), rather
 * than guessed at.
 */
@Service
public class PdfTextExtractionService {

    PdfContent extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return new PdfContent(text, document.getNumberOfPages());
        } catch (InvalidPasswordException e) {
            throw new PasswordProtectedDocumentException("This PDF is password protected and cannot be processed.");
        } catch (IOException e) {
            throw new CorruptedDocumentException(
                "Unable to process this document because the file appears to be corrupted.", e);
        }
    }

    BufferedImage renderFirstPage(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFRenderer(document).renderImageWithDPI(0, 150);
        } catch (InvalidPasswordException e) {
            throw new PasswordProtectedDocumentException("This PDF is password protected and cannot be processed.");
        } catch (IOException e) {
            throw new CorruptedDocumentException(
                "Unable to process this document because the file appears to be corrupted.", e);
        }
    }
}
