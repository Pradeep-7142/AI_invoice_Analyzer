package com.invoiceiq.export;

import com.invoiceiq.dto.InvoiceSummaryResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Minimal RFC 4180-ish CSV row formatting — quotes a field only when it actually needs it. */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static byte[] writeInvoices(List<InvoiceSummaryResponse> invoices) {
        StringBuilder sb = new StringBuilder();
        sb.append(row("ID", "Invoice Number", "Vendor", "Invoice Date", "Due Date", "Currency", "Total Amount", "Status", "Submitted By", "Created At"));
        for (InvoiceSummaryResponse inv : invoices) {
            sb.append(row(
                inv.id(),
                inv.invoiceNumber(),
                inv.vendor() != null ? inv.vendor().name() : "",
                inv.invoiceDate(),
                inv.dueDate(),
                inv.currency(),
                inv.totalAmount(),
                inv.status(),
                inv.submittedByName(),
                inv.createdAt()
            ));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String row(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(values[i]));
        }
        return line.append('\n').toString();
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
