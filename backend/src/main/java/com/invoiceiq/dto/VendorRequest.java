package com.invoiceiq.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VendorRequest(
    @NotBlank(message = "Vendor name is required.")
    @Size(max = 255)
    String name,

    @Email(message = "Email must be a valid address.")
    @Size(max = 255)
    String email,

    @Size(max = 50)
    String phone,

    String address,

    @Size(max = 20)
    String gstin,

    @Size(max = 50)
    String taxId,

    @Size(max = 100)
    String category,

    String notes
) {
}
