package com.invoiceiq.forecast;

import com.invoiceiq.dto.CashFlowForecastResponse;
import com.invoiceiq.dto.CashFlowWeekPoint;
import com.invoiceiq.dto.MonthlyProjectionResponse;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.Payment;
import com.invoiceiq.entity.PaymentStatus;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.repository.PaymentRepository;
import com.invoiceiq.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately not a predictive model: the cash-flow forecast is built
 * entirely from money that's already committed in the database (payments
 * actually scheduled, invoices actually due), bucketed by week. The one
 * genuinely projected number — {@link #monthlyProjection} — is a plain
 * historical average, labeled as exactly that in its own response, never
 * dressed up as a forecast of the committed-money kind.
 */
@Service
public class ForecastService {

    private static final Set<InvoiceStatus> PAYABLE_STATUSES = Set.of(
        InvoiceStatus.APPROVED, InvoiceStatus.PAYMENT_SCHEDULED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUser currentUser;

    public ForecastService(InvoiceRepository invoiceRepository, PaymentRepository paymentRepository, CurrentUser currentUser) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public CashFlowForecastResponse cashFlow(int weeks) {
        UUID organizationId = currentUser.organizationId();
        List<Payment> payments = paymentRepository.findByOrganizationId(organizationId);
        List<Invoice> invoices = invoiceRepository.findByOrganizationId(organizationId);

        Map<UUID, BigDecimal> completedByInvoice = payments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
            .collect(Collectors.groupingBy(p -> p.getInvoice().getId(),
                Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));
        Map<UUID, BigDecimal> scheduledByInvoice = payments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.SCHEDULED)
            .collect(Collectors.groupingBy(p -> p.getInvoice().getId(),
                Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));

        LocalDate currentWeekStart = weekStart(LocalDate.now());

        Map<LocalDate, BigDecimal> scheduledByWeek = new TreeMap<>();
        BigDecimal totalScheduled = BigDecimal.ZERO;
        for (Payment payment : payments) {
            if (payment.getStatus() != PaymentStatus.SCHEDULED) continue;
            totalScheduled = totalScheduled.add(payment.getAmount());
            LocalDate bucket = clampToCurrentWeekOrLater(weekStart(payment.getScheduledDate()), currentWeekStart);
            scheduledByWeek.merge(bucket, payment.getAmount(), BigDecimal::add);
        }

        Map<LocalDate, BigDecimal> dueByWeek = new TreeMap<>();
        BigDecimal totalDueUnscheduled = BigDecimal.ZERO;
        for (Invoice invoice : invoices) {
            if (!PAYABLE_STATUSES.contains(invoice.getStatus()) || invoice.getDueDate() == null) continue;
            BigDecimal total = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
            BigDecimal outstanding = total.subtract(completedByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO));
            BigDecimal alreadyScheduled = scheduledByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO);
            BigDecimal dueUnscheduled = outstanding.subtract(alreadyScheduled);
            if (dueUnscheduled.compareTo(BigDecimal.ZERO) <= 0) continue;

            totalDueUnscheduled = totalDueUnscheduled.add(dueUnscheduled);
            LocalDate bucket = clampToCurrentWeekOrLater(weekStart(invoice.getDueDate()), currentWeekStart);
            dueByWeek.merge(bucket, dueUnscheduled, BigDecimal::add);
        }

        List<CashFlowWeekPoint> weekPoints = java.util.stream.IntStream.range(0, weeks)
            .mapToObj(currentWeekStart::plusWeeks)
            .map(week -> new CashFlowWeekPoint(
                week,
                scheduledByWeek.getOrDefault(week, BigDecimal.ZERO),
                dueByWeek.getOrDefault(week, BigDecimal.ZERO)))
            .toList();

        return new CashFlowForecastResponse(weekPoints, totalScheduled, totalDueUnscheduled);
    }

    @Transactional(readOnly = true)
    public MonthlyProjectionResponse monthlyProjection(int months) {
        UUID organizationId = currentUser.organizationId();
        LocalDate start = YearMonth.now().minusMonths(months - 1L).atDay(1);
        List<Invoice> periodInvoices = invoiceRepository.findByOrganizationIdAndInvoiceDateBetween(organizationId, start, LocalDate.now())
            .stream()
            .filter(i -> i.getStatus() != InvoiceStatus.ARCHIVED)
            .toList();

        BigDecimal total = periodInvoices.stream()
            .map(Invoice::getTotalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        String explanation = String.format(
            "Simple average of actual spend over the last %d month%s (%s total) — a historical baseline, not a predictive model.",
            months, months == 1 ? "" : "s", total);

        return new MonthlyProjectionResponse(average, months, explanation);
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Overdue/past-due dates all land in the current week's bucket rather than a negative-index week that wouldn't render. */
    private LocalDate clampToCurrentWeekOrLater(LocalDate bucket, LocalDate currentWeekStart) {
        return bucket.isBefore(currentWeekStart) ? currentWeekStart : bucket;
    }
}
