package com.invoiceiq.dto;

import java.util.UUID;

public record DuplicateWarningDto(UUID invoiceId, String invoiceNumber, double probability, String reason) {
}
