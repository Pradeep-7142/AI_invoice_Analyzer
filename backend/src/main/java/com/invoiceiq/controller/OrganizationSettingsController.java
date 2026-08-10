package com.invoiceiq.controller;

import com.invoiceiq.dto.FinanceSettingsRequest;
import com.invoiceiq.dto.FinanceSettingsResponse;
import com.invoiceiq.service.FinanceSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/finance-settings")
@PreAuthorize("hasAuthority('ORGANIZATION_ADMIN')")
public class OrganizationSettingsController {

    private final FinanceSettingsService financeSettingsService;

    public OrganizationSettingsController(FinanceSettingsService financeSettingsService) {
        this.financeSettingsService = financeSettingsService;
    }

    @GetMapping
    public FinanceSettingsResponse get() {
        return financeSettingsService.get();
    }

    @PutMapping
    public FinanceSettingsResponse update(@Valid @RequestBody FinanceSettingsRequest request) {
        return financeSettingsService.update(request);
    }
}
