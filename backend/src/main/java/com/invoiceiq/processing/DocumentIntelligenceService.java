package com.invoiceiq.processing;

import com.invoiceiq.ai.AiExtractionService;
import com.invoiceiq.ai.ExtractedInvoiceFields;
import com.invoiceiq.entity.DocumentProcessingStatus;
import com.invoiceiq.entity.DocumentType;
import com.invoiceiq.ocr.OcrResult;
import com.invoiceiq.ocr.OcrService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the document-intelligence pipeline described in the product
 * spec: extract text (PDFBox, falling back to OCR for scanned/image
 * documents) → classify → reject if it's confidently the wrong kind of
 * document → extract structured fields with confidence scores. Never
 * throws out to the controller — every outcome, including corrupted or
 * password-protected files, comes back as a {@link DocumentAnalysisResult}
 * so the upload always succeeds and the document's fate is recorded and
 * visible, not just a transient error toast.
 */
@Service
public class DocumentIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntelligenceService.class);

    /** Below this many characters of selectable text, a PDF is treated as image-only and routed to OCR. */
    private static final int MIN_SELECTABLE_TEXT_LENGTH = 20;

    private final PdfTextExtractionService pdfTextExtractionService;
    private final OcrService ocrService;
    private final DocumentClassificationService documentClassificationService;
    private final AiExtractionService aiExtractionService;

    public DocumentIntelligenceService(
        PdfTextExtractionService pdfTextExtractionService,
        OcrService ocrService,
        DocumentClassificationService documentClassificationService,
        AiExtractionService aiExtractionService
    ) {
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.ocrService = ocrService;
        this.documentClassificationService = documentClassificationService;
        this.aiExtractionService = aiExtractionService;
    }

    public DocumentAnalysisResult analyze(byte[] content, String contentType) {
        TextSource textSource;
        try {
            textSource = extractRawText(content, contentType);
        } catch (PasswordProtectedDocumentException | CorruptedDocumentException e) {
            return new DocumentAnalysisResult(null, null, null, null,
                DocumentProcessingStatus.REJECTED, e.getMessage(), null);
        }

        ClassificationResult classification = documentClassificationService.classify(textSource.text());

        boolean confidentlyWrongType = classification.documentType() != DocumentType.UNKNOWN
            && !documentClassificationService.isInvoiceCompatible(classification.documentType());
        if (confidentlyWrongType) {
            String reason = "Document appears to be a " + humanReadable(classification.documentType()) + ", not an invoice.";
            return new DocumentAnalysisResult(
                textSource.text(), textSource.pageCount(), textSource.ocrConfidence(),
                classification.documentType(), DocumentProcessingStatus.REJECTED, reason, null);
        }

        ExtractedInvoiceFields fields = aiExtractionService.extract(textSource.text());

        DocumentProcessingStatus status = textSource.text() == null || textSource.text().isBlank()
            ? DocumentProcessingStatus.NEEDS_REVIEW
            : DocumentProcessingStatus.PROCESSED;

        return new DocumentAnalysisResult(
            textSource.text(), textSource.pageCount(), textSource.ocrConfidence(),
            classification.documentType(), status, null, fields);
    }

    private TextSource extractRawText(byte[] content, String contentType) {
        if ("application/pdf".equals(contentType)) {
            PdfContent pdfContent = pdfTextExtractionService.extractText(content);
            if (pdfContent.text() != null && pdfContent.text().trim().length() >= MIN_SELECTABLE_TEXT_LENGTH) {
                return new TextSource(pdfContent.text(), pdfContent.pageCount(), null);
            }

            if (!ocrService.isAvailable()) {
                return new TextSource(pdfContent.text(), pdfContent.pageCount(), null);
            }

            BufferedImage firstPage = pdfTextExtractionService.renderFirstPage(content);
            Optional<OcrResult> ocrResult = ocrService.recognize(firstPage);
            return ocrResult
                .map(r -> new TextSource(r.text(), pdfContent.pageCount(), r.confidence()))
                .orElse(new TextSource(pdfContent.text(), pdfContent.pageCount(), null));
        }

        if (contentType != null && contentType.startsWith("image/")) {
            if (!ocrService.isAvailable()) {
                return new TextSource("", 1, null);
            }
            BufferedImage image;
            try {
                image = ImageIO.read(new ByteArrayInputStream(content));
            } catch (IOException e) {
                log.warn("Failed to decode image for OCR: {}", e.getMessage());
                return new TextSource("", 1, null);
            }
            if (image == null) {
                return new TextSource("", 1, null);
            }
            Optional<OcrResult> ocrResult = ocrService.recognize(image);
            return ocrResult
                .map(r -> new TextSource(r.text(), 1, r.confidence()))
                .orElse(new TextSource("", 1, null));
        }

        return new TextSource("", 1, null);
    }

    private String humanReadable(DocumentType documentType) {
        return documentType.name().toLowerCase().replace('_', ' ');
    }

    private record TextSource(String text, int pageCount, Double ocrConfidence) {
    }
}
