package com.invoiceiq.controller;

import com.invoiceiq.dto.BudgetHistoryResponse;
import com.invoiceiq.dto.BudgetRequest;
import com.invoiceiq.dto.BudgetResponse;
import com.invoiceiq.dto.BudgetStatusResponse;
import com.invoiceiq.service.BudgetService;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private static final String MANAGE_BUDGETS = "hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER')";

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public List<BudgetResponse> list() {
        return budgetService.list();
    }

    @GetMapping("/status")
    public List<BudgetStatusResponse> status(@RequestParam(required = false) String month) {
        YearMonth yearMonth = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month);
        return budgetService.status(yearMonth);
    }

    @GetMapping("/history")
    public List<BudgetHistoryResponse> history(@RequestParam(defaultValue = "6") int months) {
        return budgetService.history(months);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_BUDGETS)
    public BudgetResponse create(@Valid @RequestBody BudgetRequest request) {
        return budgetService.create(request);
    }

    @PutMapping("/{budgetId}")
    @PreAuthorize(MANAGE_BUDGETS)
    public BudgetResponse update(@PathVariable UUID budgetId, @Valid @RequestBody BudgetRequest request) {
        return budgetService.update(budgetId, request);
    }

    @DeleteMapping("/{budgetId}")
    @PreAuthorize(MANAGE_BUDGETS)
    public ResponseEntity<Void> delete(@PathVariable UUID budgetId) {
        budgetService.delete(budgetId);
        return ResponseEntity.noContent().build();
    }
}
