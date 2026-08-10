package com.invoiceiq.processing;

import com.invoiceiq.entity.DocumentType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Explainable keyword-based classifier — counts how many of each
 * document type's telltale phrases appear in the extracted text and picks
 * the strongest match. It is not a machine-learned classifier, but it is
 * transparent about exactly why it decided what it decided, which matters
 * more here than raw accuracy: a wrong rejection blocks a real invoice, so
 * ties and weak signals are resolved in favor of letting a human decide
 * (UNKNOWN) rather than confidently guessing.
 */
@Service
public class DocumentClassificationService {

    private static final Map<DocumentType, List<String>> KEYWORDS = Map.of(
        DocumentType.INVOICE, List.of("tax invoice", "invoice number", "invoice date", "bill to", "invoice"),
        DocumentType.RECEIPT, List.of("receipt", "payment received", "cash receipt", "amount paid"),
        DocumentType.CREDIT_NOTE, List.of("credit note", "credit memo"),
        DocumentType.DEBIT_NOTE, List.of("debit note", "debit memo"),
        DocumentType.PURCHASE_ORDER, List.of("purchase order", "po number", "p.o. number"),
        DocumentType.STATEMENT, List.of("statement of account", "account statement", "opening balance", "closing balance")
    );

    private static final List<DocumentType> INVOICE_COMPATIBLE = List.of(
        DocumentType.INVOICE, DocumentType.RECEIPT, DocumentType.CREDIT_NOTE, DocumentType.DEBIT_NOTE);

    ClassificationResult classify(String text) {
        String lower = text == null ? "" : text.toLowerCase();

        DocumentType bestType = DocumentType.UNKNOWN;
        int bestScore = 0;

        for (Map.Entry<DocumentType, List<String>> entry : KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestType = entry.getKey();
            }
        }

        if (bestScore == 0) {
            return new ClassificationResult(DocumentType.UNKNOWN, 0.0);
        }

        double confidence = Math.min(1.0, bestScore * 0.3 + 0.4);
        return new ClassificationResult(bestType, confidence);
    }

    boolean isInvoiceCompatible(DocumentType documentType) {
        return INVOICE_COMPATIBLE.contains(documentType);
    }
}
