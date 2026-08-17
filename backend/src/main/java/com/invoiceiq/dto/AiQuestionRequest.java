package com.invoiceiq.dto;

import jakarta.validation.constraints.NotBlank;

public record AiQuestionRequest(
    @NotBlank(message = "Question cannot be empty.")
    String question
) {
}
