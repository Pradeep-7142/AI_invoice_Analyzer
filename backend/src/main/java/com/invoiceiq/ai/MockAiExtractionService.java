package com.invoiceiq.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Deterministic regex/keyword extractor standing in for a real LLM
 * provider (see {@link AiExtractionService}). Every field is either found
 * verbatim in the text with a defensible confidence, or left null — this
 * class never invents a value.
 */
@Service
public class MockAiExtractionService implements AiExtractionService {

    private static final String DATE_PATTERN =
        "(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+[A-Za-z]{3,9}\\.?\\s+\\d{4})";
    private static final String AMOUNT_PATTERN = "[₹$]?\\s*([\\d,]+(?:\\.\\d{1,2})?)";

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy"),
        DateTimeFormatter.ofPattern("d MMMM yyyy")
    );

    private static final Pattern INVOICE_NUMBER_STRONG =
        Pattern.compile("(?i)invoice\\s*(?:no\\.?|number|#)\\s*[:\\-]?\\s*([A-Za-z0-9][A-Za-z0-9/_-]{2,24})");
    private static final Pattern INVOICE_NUMBER_WEAK =
        Pattern.compile("(?i)\\binv\\.?\\s*(?:no\\.?|#)?\\s*[:\\-]\\s*([A-Za-z0-9][A-Za-z0-9/_-]{2,24})");

    private static final Pattern INVOICE_DATE = Pattern.compile("(?i)invoice\\s*date\\s*[:\\-]?\\s*" + DATE_PATTERN);
    private static final Pattern DUE_DATE = Pattern.compile("(?i)(?:due\\s*date|payment\\s*due)\\s*[:\\-]?\\s*" + DATE_PATTERN);

    private static final Pattern GRAND_TOTAL = Pattern.compile("(?i)(?:grand\\s*total|amount\\s*due|balance\\s*due)\\s*[:\\-]?\\s*" + AMOUNT_PATTERN);
    private static final Pattern GENERIC_TOTAL = Pattern.compile("(?i)\\btotal\\s*(?:amount)?\\s*[:\\-]?\\s*" + AMOUNT_PATTERN);
    private static final Pattern SUBTOTAL = Pattern.compile("(?i)sub\\s*-?\\s*total\\s*[:\\-]?\\s*" + AMOUNT_PATTERN);
    private static final Pattern TAX = Pattern.compile("(?i)\\b(?:tax|gst|vat)\\s*(?:amount)?\\s*[:\\-]?\\s*" + AMOUNT_PATTERN);

    private static final Pattern GSTIN = Pattern.compile("\\b(\\d{2}[A-Z]{5}\\d{4}[A-Z]\\d[Z][A-Z0-9])\\b");

    private static final List<String> GENERIC_HEADER_WORDS = List.of(
        "invoice", "tax invoice", "receipt", "bill", "credit note", "debit note", "statement", "purchase order"
    );

    @Override
    public ExtractedInvoiceFields extract(String text) {
        String normalized = text == null ? "" : text;
        Map<String, Double> confidences = new HashMap<>();

        String invoiceNumber = extractInvoiceNumber(normalized, confidences);
        LocalDate invoiceDate = extractDate(INVOICE_DATE, normalized, "invoiceDate", confidences, 0.85);
        LocalDate dueDate = extractDate(DUE_DATE, normalized, "dueDate", confidences, 0.85);
        BigDecimal totalAmount = extractTotal(normalized, confidences);
        BigDecimal subtotalAmount = extractAmount(SUBTOTAL, normalized, "subtotalAmount", confidences, 0.8);
        BigDecimal taxAmount = extractAmount(TAX, normalized, "taxAmount", confidences, 0.75);
        String gstin = extractGstin(normalized, confidences);

        // Only guess a vendor name (weak first-line heuristic) once some
        // other field gives real confidence this is actually an invoice —
        // otherwise unrelated text would always yield a "vendor" guess.
        boolean hasInvoiceSignal = invoiceNumber != null || totalAmount != null || gstin != null;
        String vendorNameRaw = hasInvoiceSignal ? extractVendorName(normalized, confidences) : null;

        return new ExtractedInvoiceFields(
            vendorNameRaw, invoiceNumber, invoiceDate, dueDate,
            subtotalAmount, taxAmount, totalAmount, gstin, confidences);
    }

    private String extractInvoiceNumber(String text, Map<String, Double> confidences) {
        Matcher strong = INVOICE_NUMBER_STRONG.matcher(text);
        if (strong.find()) {
            confidences.put("invoiceNumber", 0.92);
            return strong.group(1);
        }
        Matcher weak = INVOICE_NUMBER_WEAK.matcher(text);
        if (weak.find()) {
            confidences.put("invoiceNumber", 0.65);
            return weak.group(1);
        }
        return null;
    }

    private LocalDate extractDate(Pattern pattern, String text, String fieldName, Map<String, Double> confidences, double confidence) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        Optional<LocalDate> parsed = parseDate(matcher.group(1));
        if (parsed.isEmpty()) {
            return null;
        }
        confidences.put(fieldName, confidence);
        return parsed.get();
    }

    private Optional<LocalDate> parseDate(String raw) {
        String candidate = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(candidate, formatter));
            } catch (DateTimeParseException ignored) {
                // try the next known format
            }
        }
        return Optional.empty();
    }

    private BigDecimal extractTotal(String text, Map<String, Double> confidences) {
        Matcher strong = GRAND_TOTAL.matcher(text);
        BigDecimal lastStrongMatch = null;
        while (strong.find()) {
            lastStrongMatch = parseAmount(strong.group(1));
        }
        if (lastStrongMatch != null) {
            confidences.put("totalAmount", 0.9);
            return lastStrongMatch;
        }

        Matcher generic = GENERIC_TOTAL.matcher(text);
        BigDecimal lastGenericMatch = null;
        while (generic.find()) {
            lastGenericMatch = parseAmount(generic.group(1));
        }
        if (lastGenericMatch != null) {
            confidences.put("totalAmount", 0.55);
        }
        return lastGenericMatch;
    }

    private BigDecimal extractAmount(Pattern pattern, String text, String fieldName, Map<String, Double> confidences, double confidence) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal amount = parseAmount(matcher.group(1));
        if (amount != null) {
            confidences.put(fieldName, confidence);
        }
        return amount;
    }

    private BigDecimal parseAmount(String raw) {
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractGstin(String text, Map<String, Double> confidences) {
        Matcher matcher = GSTIN.matcher(text);
        if (matcher.find()) {
            confidences.put("gstin", 0.95);
            return matcher.group(1);
        }
        return null;
    }

    private String extractVendorName(String text, Map<String, Double> confidences) {
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.length() < 3 || trimmed.length() > 80) {
                continue;
            }
            String lower = trimmed.toLowerCase();
            boolean isGenericHeader = GENERIC_HEADER_WORDS.stream().anyMatch(lower::equals);
            boolean hasLetters = trimmed.chars().anyMatch(Character::isLetter);
            if (!isGenericHeader && hasLetters) {
                confidences.put("vendorName", 0.4);
                return trimmed;
            }
        }
        return null;
    }
}
