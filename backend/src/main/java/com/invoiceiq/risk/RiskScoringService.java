package com.invoiceiq.risk;

import com.invoiceiq.validation.ValidationResult;
import com.invoiceiq.validation.ValidationStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Combines every explainable signal this build has (validation failures,
 * duplicate matches, amount anomalies, an unresolved vendor) into a single
 * 0–100 score — always with the itemized reasons attached. A risk number
 * with no explanation is a black box; every point here traces back to a
 * specific, visible finding.
 */
@Service
public class RiskScoringService {

    public RiskScore score(
        List<ValidationResult> validationResults,
        List<DuplicateWarning> duplicates,
        Optional<AnomalyFinding> anomaly,
        boolean vendorUnresolved
    ) {
        int total = 0;
        List<String> reasons = new ArrayList<>();

        Optional<DuplicateWarning> topDuplicate = duplicates.stream()
            .max(Comparator.comparingDouble(DuplicateWarning::probability));
        if (topDuplicate.isPresent()) {
            DuplicateWarning warning = topDuplicate.get();
            int contribution = Math.min(40, (int) Math.round(warning.probability() * 40));
            total += contribution;
            reasons.add("Possible duplicate of invoice " + warning.invoiceNumber()
                + " (" + Math.round(warning.probability() * 100) + "% match).");
        }

        if (anomaly.isPresent()) {
            AnomalyFinding finding = anomaly.get();
            total += "HIGH".equals(finding.severity()) ? 30 : 15;
            reasons.add(finding.explanation());
        }

        long errorCount = validationResults.stream().filter(r -> r.status() == ValidationStatus.ERROR).count();
        long warningCount = validationResults.stream().filter(r -> r.status() == ValidationStatus.WARNING).count();

        if (errorCount > 0) {
            total += Math.min(30, (int) errorCount * 15);
            reasons.add(errorCount + " validation error" + (errorCount == 1 ? "" : "s")
                + " found — required data is missing or invalid.");
        }
        if (warningCount > 0) {
            total += Math.min(15, (int) warningCount * 5);
            reasons.add(warningCount + " validation warning" + (warningCount == 1 ? "" : "s")
                + " found — figures don't fully reconcile.");
        }

        if (vendorUnresolved) {
            total += 5;
            reasons.add("Vendor could not be automatically matched to an existing vendor record.");
        }

        return new RiskScore(Math.min(100, total), reasons);
    }
}
