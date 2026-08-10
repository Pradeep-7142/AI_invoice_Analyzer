package com.invoiceiq.risk;

import com.invoiceiq.entity.Invoice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Flags an invoice as part of a probable recurring series (subscriptions,
 * rent, retainers) — purely informational, surfaced the same way as an
 * anomaly finding. Deliberately conservative: requires at least 3 dated
 * invoices from the vendor with amounts within 25% of each other AND every
 * consecutive gap landing in a monthly window (25-35 days). One irregular
 * gap or one outlier amount and this stays silent rather than guessing.
 */
@Service
public class RecurringExpenseDetectionService {

    private static final int MIN_OCCURRENCES = 3;
    private static final long MONTHLY_MIN_DAYS = 25;
    private static final long MONTHLY_MAX_DAYS = 35;
    private static final double AMOUNT_TOLERANCE_RATIO = 0.25;

    public Optional<RecurringExpenseFinding> detect(Invoice invoice, List<Invoice> sameVendorHistory) {
        List<Invoice> all = new ArrayList<>(sameVendorHistory);
        all.add(invoice);

        List<Invoice> dated = all.stream()
            .filter(i -> i.getInvoiceDate() != null && i.getTotalAmount() != null)
            .sorted(Comparator.comparing(Invoice::getInvoiceDate))
            .toList();

        if (dated.size() < MIN_OCCURRENCES) {
            return Optional.empty();
        }

        double average = dated.stream().mapToDouble(i -> i.getTotalAmount().doubleValue()).average().orElse(0);
        if (average <= 0) {
            return Optional.empty();
        }

        boolean allAmountsClose = dated.stream()
            .allMatch(i -> Math.abs(i.getTotalAmount().doubleValue() - average) <= average * AMOUNT_TOLERANCE_RATIO);
        if (!allAmountsClose) {
            return Optional.empty();
        }

        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < dated.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(dated.get(i - 1).getInvoiceDate(), dated.get(i).getInvoiceDate()));
        }

        boolean allGapsMonthly = gaps.stream().allMatch(g -> g >= MONTHLY_MIN_DAYS && g <= MONTHLY_MAX_DAYS);
        if (!allGapsMonthly) {
            return Optional.empty();
        }

        LocalDate lastDate = dated.get(dated.size() - 1).getInvoiceDate();
        LocalDate expectedNext = lastDate.plusDays(30);
        BigDecimal averageAmount = BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);

        String explanation = String.format(
            "This vendor has billed a similar amount (~%s %s) roughly every month across %d consecutive invoices — expected again around %s.",
            invoice.getCurrency(), averageAmount, dated.size(), expectedNext);

        return Optional.of(new RecurringExpenseFinding("MONTHLY", dated.size(), averageAmount, expectedNext, explanation));
    }
}
