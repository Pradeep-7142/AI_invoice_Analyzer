package com.invoiceiq.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoiceiq.validation.ValidationResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskScoringServiceTest {

    private final RiskScoringService service = new RiskScoringService();

    @Test
    void noSignalsMeansZeroRiskAndNoReasons() {
        RiskScore score = service.score(List.of(), List.of(), Optional.empty(), false);

        assertThat(score.score()).isZero();
        assertThat(score.reasons()).isEmpty();
    }

    @Test
    void anExactDuplicateContributesTheLargestSingleSignal() {
        DuplicateWarning duplicate = new DuplicateWarning(UUID.randomUUID(), "INV-1", 0.98, "Exact match.");

        RiskScore score = service.score(List.of(), List.of(duplicate), Optional.empty(), false);

        assertThat(score.score()).isGreaterThan(30);
        assertThat(score.reasons()).anyMatch(r -> r.contains("duplicate"));
    }

    @Test
    void aHighAnomalyScoresMoreThanAMediumAnomaly() {
        RiskScore high = service.score(List.of(), List.of(), Optional.of(new AnomalyFinding("HIGH", "way above average")), false);
        RiskScore medium = service.score(List.of(), List.of(), Optional.of(new AnomalyFinding("MEDIUM", "somewhat above average")), false);

        assertThat(high.score()).isGreaterThan(medium.score());
    }

    @Test
    void validationErrorsAndWarningsBothContributeAndAreExplained() {
        List<ValidationResult> results = List.of(
            ValidationResult.error("VENDOR_PRESENT", "Vendor is missing."),
            ValidationResult.warning("TOTALS_CONSISTENT", "Totals don't add up."));

        RiskScore score = service.score(results, List.of(), Optional.empty(), false);

        assertThat(score.score()).isGreaterThan(0);
        assertThat(score.reasons()).anyMatch(r -> r.contains("error"));
        assertThat(score.reasons()).anyMatch(r -> r.contains("warning"));
    }

    @Test
    void anUnresolvedVendorAddsASmallExplainedSignal() {
        RiskScore score = service.score(List.of(), List.of(), Optional.empty(), true);

        assertThat(score.score()).isEqualTo(5);
        assertThat(score.reasons()).anyMatch(r -> r.contains("Vendor could not be automatically matched"));
    }

    @Test
    void combinedScoreIsCappedAtOneHundred() {
        DuplicateWarning duplicate = new DuplicateWarning(UUID.randomUUID(), "INV-1", 0.98, "Exact match.");
        List<ValidationResult> manyErrors = List.of(
            ValidationResult.error("A", "a"), ValidationResult.error("B", "b"),
            ValidationResult.error("C", "c"), ValidationResult.error("D", "d"));

        RiskScore score = service.score(manyErrors, List.of(duplicate),
            Optional.of(new AnomalyFinding("HIGH", "way above average")), true);

        assertThat(score.score()).isEqualTo(100);
    }
}
