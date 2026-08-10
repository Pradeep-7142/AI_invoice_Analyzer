package com.invoiceiq.risk;

import java.util.UUID;

public record DuplicateWarning(UUID invoiceId, String invoiceNumber, double probability, String reason) {
}
