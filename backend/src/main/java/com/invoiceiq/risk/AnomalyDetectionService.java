package com.invoiceiq.risk;

import com.invoiceiq.entity.Invoice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Statistical (z-score) baseline check against a vendor's own history —
 * intentionally simple: with only a handful of invoices per vendor at this
 * scale, a fitted ML model would be overfit noise, not a real baseline.
 * Requires at least 3 prior invoices with a total amount; otherwise there
 * is no baseline to compare against, and silence is more honest than a
 * guess.
 */
@Service
public class AnomalyDetectionService {

    private static final int MIN_HISTORY_SIZE = 3;
    private static final double HIGH_Z_THRESHOLD = 3.0;
    private static final double MEDIUM_Z_THRESHOLD = 2.0;

    public Optional<AnomalyFinding> detect(Invoice invoice, List<Invoice> sameVendorHistory) {
        if (invoice.getTotalAmount() == null) {
            return Optional.empty();
        }

        List<Double> historicalAmounts = sameVendorHistory.stream()
            .map(Invoice::getTotalAmount)
            .filter(a -> a != null)
            .map(BigDecimal::doubleValue)
            .toList();

        if (historicalAmounts.size() < MIN_HISTORY_SIZE) {
            return Optional.empty();
        }

        double current = invoice.getTotalAmount().doubleValue();
        double mean = historicalAmounts.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = historicalAmounts.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum() / (historicalAmounts.size() - 1);
        double stdDev = Math.sqrt(variance);

        double zScore = stdDev == 0
            ? (current == mean ? 0 : Double.POSITIVE_INFINITY)
            : Math.abs(current - mean) / stdDev;

        if (zScore < MEDIUM_Z_THRESHOLD) {
            return Optional.empty();
        }

        String severity = zScore >= HIGH_Z_THRESHOLD ? "HIGH" : "MEDIUM";
        BigDecimal ratio = mean == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(current / mean).setScale(1, RoundingMode.HALF_UP);
        String explanation = String.format(
            "This invoice's amount is %s× the vendor's historical average (based on %d prior invoice%s).",
            ratio, historicalAmounts.size(), historicalAmounts.size() == 1 ? "" : "s");

        return Optional.of(new AnomalyFinding(severity, explanation));
    }
}
