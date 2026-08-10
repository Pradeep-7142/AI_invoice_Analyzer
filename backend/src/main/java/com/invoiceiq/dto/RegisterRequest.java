package com.invoiceiq.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Organization name is required.")
    @Size(max = 255)
    String organizationName,

    @NotBlank(message = "Full name is required.")
    @Size(max = 255)
    String fullName,

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be a valid address.")
    @Size(max = 255)
    String email,

    @NotBlank(message = "Password is required.")
    @Size(min = 8, max = 128, message = "Password must be at least 8 characters.")
    String password
) {
}
