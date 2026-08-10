package com.invoiceiq.config;

import com.invoiceiq.entity.Budget;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceLineItem;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.MembershipStatus;
import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.OrganizationMember;
import com.invoiceiq.entity.Payment;
import com.invoiceiq.entity.PaymentMethod;
import com.invoiceiq.entity.PaymentStatus;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.repository.BudgetRepository;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.repository.OrganizationMemberRepository;
import com.invoiceiq.repository.OrganizationRepository;
import com.invoiceiq.repository.PaymentRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final VendorRepository vendorRepository;
    private final BudgetRepository budgetRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
        OrganizationRepository organizationRepository,
        UserAccountRepository userAccountRepository,
        OrganizationMemberRepository organizationMemberRepository,
        VendorRepository vendorRepository,
        BudgetRepository budgetRepository,
        InvoiceRepository invoiceRepository,
        PaymentRepository paymentRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.vendorRepository = vendorRepository;
        this.budgetRepository = budgetRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking and seeding demonstration data for InvoiceIQ...");

        // 1. Organization
        Organization org = organizationRepository.findBySlug("acme-technologies")
            .orElseGet(() -> {
                Organization newOrg = new Organization("Acme Technologies Inc.", "acme-technologies");
                newOrg.setManagerApprovalThreshold(new BigDecimal("50000.00"));
                newOrg.setAdminApprovalThreshold(new BigDecimal("200000.00"));
                return organizationRepository.save(newOrg);
            });

        // 2. Provision Users
        String encodedPassword = passwordEncoder.encode("Password123!");
        UserAccount admin = ensureUser(org, "admin@acme.com", encodedPassword, "Arun Sharma (Admin)", OrgRole.ORGANIZATION_ADMIN);
        UserAccount financeMgr = ensureUser(org, "finance@acme.com", encodedPassword, "Priya Patel (Finance Mgr)", OrgRole.FINANCE_MANAGER);
        UserAccount accountant = ensureUser(org, "accountant@acme.com", encodedPassword, "Vikram Malhotra (Accountant)", OrgRole.ACCOUNTANT);
        UserAccount employee = ensureUser(org, "employee@acme.com", encodedPassword, "Sneha Reddy (Employee)", OrgRole.EMPLOYEE);

        // 3. Provision Vendors
        Map<String, Vendor> vendorMap = new HashMap<>();
        List<Vendor> existingVendors = vendorRepository.findByOrganizationId(org.getId());
        for (Vendor v : existingVendors) {
            vendorMap.put(v.getName(), v);
        }

        Vendor aws = getOrCreateVendor(org, vendorMap, "Amazon Web Services (AWS)", "billing@aws.amazon.com", "Cloud", "29AABCA1234F1Z5");
        Vendor gcp = getOrCreateVendor(org, vendorMap, "Google Cloud Platform", "billing@google.com", "Cloud", "07AABCG5678K1ZQ");
        Vendor azure = getOrCreateVendor(org, vendorMap, "Microsoft Azure", "invoicing@microsoft.com", "Cloud", "33AABCM9012P1ZX");
        Vendor slack = getOrCreateVendor(org, vendorMap, "Slack Technologies", "ar@slack.com", "Software", "27AABCS3456L1Z2");
        Vendor github = getOrCreateVendor(org, vendorMap, "GitHub Inc.", "billing@github.com", "Software", "29AABCG7890M1Z8");
        Vendor notion = getOrCreateVendor(org, vendorMap, "Notion Labs", "billing@makenotion.com", "Software", "06AABCN2345N1Z4");
        Vendor wework = getOrCreateVendor(org, vendorMap, "WeWork India", "accounts@wework.co.in", "Office", "29AABCW6789O1Z9");
        Vendor dell = getOrCreateVendor(org, vendorMap, "Dell Technologies", "enterprise-orders@dell.com", "Equipment", "29AABCD1111P1Z1");
        Vendor kpmg = getOrCreateVendor(org, vendorMap, "KPMG Advisory Services", "invoicing@kpmg.com", "Professional Services", "27AABCK2222Q1Z3");
        Vendor uber = getOrCreateVendor(org, vendorMap, "Uber for Business", "business-support@uber.com", "Travel", "27AABCU3333R1Z5");

        // 4. Provision Budgets
        if (budgetRepository.findByOrganizationIdOrderByCategoryAsc(org.getId()).isEmpty()) {
            budgetRepository.saveAll(List.of(
                new Budget(org, "Cloud", new BigDecimal("250000.00"), "INR"),
                new Budget(org, "Software", new BigDecimal("180000.00"), "INR"),
                new Budget(org, "Office", new BigDecimal("100000.00"), "INR"),
                new Budget(org, "Professional Services", new BigDecimal("300000.00"), "INR"),
                new Budget(org, "Travel", new BigDecimal("80000.00"), "INR"),
                new Budget(org, "Equipment", new BigDecimal("150000.00"), "INR")
            ));
            log.info("Budgets successfully seeded for {}", org.getName());
        }

        // 5. Provision Invoices if none exist for this organization
        List<Invoice> existingInvoices = invoiceRepository.findByOrganizationId(org.getId());
        if (!existingInvoices.isEmpty()) {
            log.info("{} invoices already exist for organization {}. Skipping invoice seed.", existingInvoices.size(), org.getName());
            return;
        }

        log.info("Seeding 11 realistic invoices for organization {}...", org.getName());
        LocalDate today = LocalDate.now();

        // 1. AWS Monthly Infrastructure (Paid)
        Invoice inv1 = createInvoice(org, aws, accountant, "INV-AWS-2026-01", today.minusMonths(1).withDayOfMonth(5), today.minusDays(5),
            new BigDecimal("145000.00"), new BigDecimal("26100.00"), new BigDecimal("0.00"), new BigDecimal("171100.00"), InvoiceStatus.PAID);
        addLineItem(inv1, 1, "AWS EC2 & RDS Multi-AZ Production Clusters", new BigDecimal("1.000"), new BigDecimal("145000.00"), new BigDecimal("26100.00"), new BigDecimal("0.00"), new BigDecimal("171100.00"));
        invoiceRepository.save(inv1);
        recordPayment(org, inv1, inv1.getTotalAmount(), PaymentMethod.BANK_TRANSFER, PaymentStatus.COMPLETED, today.minusDays(6), "UTR998822114");

        // 2. Google Cloud Compute (Paid)
        Invoice inv2 = createInvoice(org, gcp, accountant, "INV-GCP-8849", today.minusMonths(2).withDayOfMonth(10), today.minusMonths(1).withDayOfMonth(10),
            new BigDecimal("68500.00"), new BigDecimal("12330.00"), new BigDecimal("0.00"), new BigDecimal("80830.00"), InvoiceStatus.PAID);
        addLineItem(inv2, 1, "Google Kubernetes Engine (GKE) Staging Workloads", new BigDecimal("1.000"), new BigDecimal("68500.00"), new BigDecimal("12330.00"), new BigDecimal("0.00"), new BigDecimal("80830.00"));
        invoiceRepository.save(inv2);
        recordPayment(org, inv2, inv2.getTotalAmount(), PaymentMethod.CARD, PaymentStatus.COMPLETED, today.minusMonths(1).withDayOfMonth(9), "CC-AUTH-44910");

        // 3. Slack Enterprise (Paid)
        Invoice inv3 = createInvoice(org, slack, employee, "INV-SLACK-4920", today.minusMonths(3).withDayOfMonth(15), today.minusMonths(2).withDayOfMonth(15),
            new BigDecimal("42000.00"), new BigDecimal("7560.00"), new BigDecimal("0.00"), new BigDecimal("49560.00"), InvoiceStatus.PAID);
        addLineItem(inv3, 1, "Slack Business+ Annual Seats (50 Users)", new BigDecimal("50.000"), new BigDecimal("840.00"), new BigDecimal("7560.00"), new BigDecimal("0.00"), new BigDecimal("49560.00"));
        invoiceRepository.save(inv3);
        recordPayment(org, inv3, inv3.getTotalAmount(), PaymentMethod.CARD, PaymentStatus.COMPLETED, today.minusMonths(2).withDayOfMonth(14), "CC-AUTH-77301");

        // 4. GitHub Enterprise (Paid)
        Invoice inv4 = createInvoice(org, github, employee, "INV-GH-11029", today.minusMonths(4).withDayOfMonth(1), today.minusMonths(3).withDayOfMonth(1),
            new BigDecimal("35000.00"), new BigDecimal("6300.00"), new BigDecimal("0.00"), new BigDecimal("41300.00"), InvoiceStatus.PAID);
        addLineItem(inv4, 1, "GitHub Enterprise Cloud Seats & GitHub Copilot Addon", new BigDecimal("35.000"), new BigDecimal("1000.00"), new BigDecimal("6300.00"), new BigDecimal("0.00"), new BigDecimal("41300.00"));
        invoiceRepository.save(inv4);
        recordPayment(org, inv4, inv4.getTotalAmount(), PaymentMethod.BANK_TRANSFER, PaymentStatus.COMPLETED, today.minusMonths(3).withDayOfMonth(1), "UTR112233445");

        // 5. Notion Workspace (Paid)
        Invoice inv5 = createInvoice(org, notion, employee, "INV-NTN-3391", today.minusMonths(1).withDayOfMonth(20), today.minusDays(10),
            new BigDecimal("18500.00"), new BigDecimal("3330.00"), new BigDecimal("0.00"), new BigDecimal("21830.00"), InvoiceStatus.PAID);
        addLineItem(inv5, 1, "Notion Workspace Plus with AI Assistant Addon", new BigDecimal("25.000"), new BigDecimal("740.00"), new BigDecimal("3330.00"), new BigDecimal("0.00"), new BigDecimal("21830.00"));
        invoiceRepository.save(inv5);
        recordPayment(org, inv5, inv5.getTotalAmount(), PaymentMethod.CARD, PaymentStatus.COMPLETED, today.minusDays(11), "CC-AUTH-88291");

        // 6. WeWork Monthly Office (Approved - Due in 5 days)
        Invoice inv6 = createInvoice(org, wework, accountant, "INV-WW-2026-08", today.minusDays(10), today.plusDays(5),
            new BigDecimal("85000.00"), new BigDecimal("15300.00"), new BigDecimal("0.00"), new BigDecimal("100300.00"), InvoiceStatus.APPROVED);
        addLineItem(inv6, 1, "Dedicated 20-Desk Private Office Suite Bangalore", new BigDecimal("1.000"), new BigDecimal("85000.00"), new BigDecimal("15300.00"), new BigDecimal("0.00"), new BigDecimal("100300.00"));
        invoiceRepository.save(inv6);

        // 7. Dell Server Expansion (Pending Approval - High Value ₹2.45L)
        Invoice inv7 = createInvoice(org, dell, employee, "INV-DELL-99201", today.minusDays(3), today.plusDays(20),
            new BigDecimal("245000.00"), new BigDecimal("44100.00"), new BigDecimal("5000.00"), new BigDecimal("284100.00"), InvoiceStatus.PENDING_APPROVAL);
        addLineItem(inv7, 1, "Dell PowerEdge R750 2U Rack Server + 128GB RAM", new BigDecimal("1.000"), new BigDecimal("245000.00"), new BigDecimal("44100.00"), new BigDecimal("5000.00"), new BigDecimal("284100.00"));
        invoiceRepository.save(inv7);

        // 8. KPMG Tax Audit (Needs Review)
        Invoice inv8 = createInvoice(org, kpmg, accountant, "INV-KPMG-5510", today.minusDays(2), today.plusDays(25),
            new BigDecimal("180000.00"), new BigDecimal("32400.00"), new BigDecimal("0.00"), new BigDecimal("212400.00"), InvoiceStatus.NEEDS_REVIEW);
        addLineItem(inv8, 1, "Half-Yearly Statutory Financial Audit & Transfer Pricing Advisory", new BigDecimal("1.000"), new BigDecimal("180000.00"), new BigDecimal("32400.00"), new BigDecimal("0.00"), new BigDecimal("212400.00"));
        invoiceRepository.save(inv8);

        // 9. AWS Cloud Data Egress (Overdue - was due 12 days ago)
        Invoice inv9 = createInvoice(org, aws, accountant, "INV-AWS-OD-901", today.minusDays(40), today.minusDays(12),
            new BigDecimal("74200.00"), new BigDecimal("13356.00"), new BigDecimal("0.00"), new BigDecimal("87556.00"), InvoiceStatus.OVERDUE);
        addLineItem(inv9, 1, "AWS CloudFront CDN Egress & Data Transfer Out", new BigDecimal("1.000"), new BigDecimal("74200.00"), new BigDecimal("13356.00"), new BigDecimal("0.00"), new BigDecimal("87556.00"));
        invoiceRepository.save(inv9);

        // 10. Uber Business Travel (Disputed)
        Invoice inv10 = createInvoice(org, uber, employee, "INV-UBER-7712", today.minusDays(15), today.plusDays(15),
            new BigDecimal("28400.00"), new BigDecimal("1420.00"), new BigDecimal("0.00"), new BigDecimal("29820.00"), InvoiceStatus.DISPUTED);
        inv10.setDisputeReason("Duplicate airport toll surcharges billed twice for leadership offsite trip.");
        addLineItem(inv10, 1, "Executive Airport Transfers & City Rides for Annual Offsite", new BigDecimal("1.000"), new BigDecimal("28400.00"), new BigDecimal("1420.00"), new BigDecimal("0.00"), new BigDecimal("29820.00"));
        invoiceRepository.save(inv10);

        // 11. Microsoft Azure Scheduled Payment (Payment Scheduled)
        Invoice inv11 = createInvoice(org, azure, accountant, "INV-AZ-66290", today.minusDays(8), today.plusDays(12),
            new BigDecimal("92000.00"), new BigDecimal("16560.00"), new BigDecimal("0.00"), new BigDecimal("108560.00"), InvoiceStatus.PAYMENT_SCHEDULED);
        addLineItem(inv11, 1, "Azure OpenAI Service Tokens & Cognitive Search Indexing", new BigDecimal("1.000"), new BigDecimal("92000.00"), new BigDecimal("16560.00"), new BigDecimal("0.00"), new BigDecimal("108560.00"));
        invoiceRepository.save(inv11);
        recordPayment(org, inv11, inv11.getTotalAmount(), PaymentMethod.BANK_TRANSFER, PaymentStatus.SCHEDULED, today.plusDays(4), "REF-AZURE-SCHEDULED");

        log.info("Demo data successfully seeded: 1 Organization, 4 Users, 10 Vendors, 6 Budgets, 11 Invoices with payments.");
    }

    private UserAccount ensureUser(Organization org, String email, String encodedPassword, String fullName, OrgRole role) {
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email).map(existing -> {
            existing.setPasswordHash(encodedPassword);
            return userAccountRepository.save(existing);
        }).orElseGet(() -> {
            UserAccount newUser = new UserAccount(email, encodedPassword, fullName);
            return userAccountRepository.save(newUser);
        });

        if (organizationMemberRepository.findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE).isEmpty()) {
            organizationMemberRepository.save(new OrganizationMember(org, user, role));
        }

        return user;
    }

    private Vendor getOrCreateVendor(Organization org, Map<String, Vendor> vendorMap, String name, String email, String category, String gstin) {
        if (vendorMap.containsKey(name)) {
            return vendorMap.get(name);
        }
        Vendor v = new Vendor(org, name);
        v.setEmail(email);
        v.setCategory(category);
        v.setGstin(gstin);
        v.setAddress("Level 5, Tech Park, Bangalore, Karnataka - 560103");
        Vendor saved = vendorRepository.save(v);
        vendorMap.put(name, saved);
        return saved;
    }

    private Invoice createInvoice(
        Organization org,
        Vendor vendor,
        UserAccount submitter,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal discount,
        BigDecimal total,
        InvoiceStatus status
    ) {
        Invoice invoice = new Invoice(org, submitter);
        invoice.setVendor(vendor);
        invoice.setVendorNameRaw(vendor.getName());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(dueDate);
        invoice.setSubtotalAmount(subtotal);
        invoice.setTaxAmount(tax);
        invoice.setDiscountAmount(discount);
        invoice.setTotalAmount(total);
        invoice.setStatus(status);
        invoice.setFieldConfidence(Map.of(
            "invoiceNumber", 0.98,
            "vendorName", 0.99,
            "totalAmount", 0.97,
            "invoiceDate", 0.95
        ));
        return invoice;
    }

    private void addLineItem(Invoice invoice, int order, String description, BigDecimal qty, BigDecimal unitPrice, BigDecimal tax, BigDecimal discount, BigDecimal total) {
        InvoiceLineItem item = new InvoiceLineItem(invoice, order, description, qty, unitPrice, tax, discount, total);
        invoice.getLineItems().add(item);
    }

    private void recordPayment(Organization org, Invoice invoice, BigDecimal amount, PaymentMethod method, PaymentStatus status, LocalDate scheduledDate, String reference) {
        Payment payment = new Payment(org, invoice, amount, "INR", method, scheduledDate, reference, null, invoice.getSubmittedBy());
        payment.setStatus(status);
        if (status == PaymentStatus.COMPLETED) {
            payment.setCompletedAt(Instant.now());
        }
        paymentRepository.save(payment);
    }
}
