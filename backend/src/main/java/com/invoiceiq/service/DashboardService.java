package com.invoiceiq.service;

import com.invoiceiq.dto.DashboardSummaryDto;
import com.invoiceiq.dto.InvoiceSummaryResponse;
import com.invoiceiq.dto.VendorSummaryDto;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final CurrentUser currentUser;

    public DashboardService(InvoiceRepository invoiceRepository, CurrentUser currentUser) {
        this.invoiceRepository = invoiceRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary() {
        List<Invoice> invoices = currentUser.isAdmin()
            ? invoiceRepository.findAll()
            : invoiceRepository.findBySubmittedById(currentUser.userId());

        long totalInvoices = invoices.size();
        long needsReview = invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.NEEDS_REVIEW).count();
        long verified = invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.VERIFIED).count();
        long approved = invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.APPROVED).count();
        long rejected = invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.REJECTED).count();

        BigDecimal totalSpend = invoices.stream()
            .filter(i -> i.getStatus() == InvoiceStatus.APPROVED)
            .map(Invoice::getTotalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingSpend = invoices.stream()
            .filter(i -> i.getStatus() == InvoiceStatus.NEEDS_REVIEW || i.getStatus() == InvoiceStatus.VERIFIED)
            .map(Invoice::getTotalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> statusBreakdown = invoices.stream()
            .collect(Collectors.groupingBy(i -> i.getStatus().name(), Collectors.counting()));

        List<InvoiceSummaryResponse> recentInvoices = invoices.stream()
            .sorted(Comparator.comparing(Invoice::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5)
            .map(this::toSummary)
            .toList();

        // Top vendors
        Map<String, List<Invoice>> byVendor = invoices.stream()
            .filter(i -> i.getVendor() != null || (i.getVendorNameRaw() != null && !i.getVendorNameRaw().isBlank()))
            .collect(Collectors.groupingBy(i -> i.getVendor() != null ? i.getVendor().getName() : i.getVendorNameRaw()));

        List<DashboardSummaryDto.VendorSpendDto> topVendors = byVendor.entrySet().stream()
            .map(entry -> {
                BigDecimal sum = entry.getValue().stream()
                    .map(Invoice::getTotalAmount)
                    .filter(a -> a != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new DashboardSummaryDto.VendorSpendDto(entry.getKey(), sum, entry.getValue().size());
            })
            .sorted(Comparator.comparing(DashboardSummaryDto.VendorSpendDto::totalAmount).reversed())
            .limit(5)
            .toList();

        // Monthly trends (last 6 months)
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy");
        LocalDate now = LocalDate.now();
        Map<String, MonthlyAggregator> monthMap = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate m = now.minusMonths(i);
            monthMap.put(m.format(monthFormatter), new MonthlyAggregator());
        }

        for (Invoice inv : invoices) {
            LocalDate date = inv.getInvoiceDate() != null ? inv.getInvoiceDate() : (inv.getCreatedAt() != null ? inv.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate() : null);
            if (date != null) {
                String label = date.format(monthFormatter);
                if (monthMap.containsKey(label)) {
                    monthMap.get(label).add(inv.getTotalAmount());
                }
            }
        }

        List<DashboardSummaryDto.MonthlyTrendDto> monthlyTrends = monthMap.entrySet().stream()
            .map(e -> new DashboardSummaryDto.MonthlyTrendDto(e.getKey(), e.getValue().total, e.getValue().count))
            .toList();

        return new DashboardSummaryDto(
            totalInvoices,
            needsReview,
            verified,
            approved,
            rejected,
            totalSpend,
            pendingSpend,
            recentInvoices,
            topVendors,
            monthlyTrends,
            statusBreakdown
        );
    }

    private static class MonthlyAggregator {
        BigDecimal total = BigDecimal.ZERO;
        long count = 0;

        void add(BigDecimal amount) {
            if (amount != null) {
                total = total.add(amount);
            }
            count++;
        }
    }

    private InvoiceSummaryResponse toSummary(Invoice invoice) {
        VendorSummaryDto vendorDto = invoice.getVendor() != null
            ? new VendorSummaryDto(invoice.getVendor().getId(), invoice.getVendor().getName())
            : null;

        return new InvoiceSummaryResponse(
            invoice.getId(),
            vendorDto,
            invoice.getInvoiceNumber(),
            invoice.getInvoiceDate(),
            invoice.getDueDate(),
            invoice.getCurrency(),
            invoice.getTotalAmount(),
            invoice.getStatus(),
            invoice.getSubmittedBy() != null ? invoice.getSubmittedBy().getFullName() : "—",
            invoice.getCreatedAt()
        );
    }
}
