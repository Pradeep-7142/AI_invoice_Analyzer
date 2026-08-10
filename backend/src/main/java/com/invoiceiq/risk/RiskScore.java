package com.invoiceiq.risk;

import java.util.List;

public record RiskScore(int score, List<String> reasons) {
}
