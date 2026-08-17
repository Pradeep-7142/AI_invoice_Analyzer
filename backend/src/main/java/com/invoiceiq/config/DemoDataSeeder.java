package com.invoiceiq.config;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceLineItem;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.UserRole;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.repository.VendorRepository;
import java.math.BigDecimal;
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

    private final UserAccountRepository userAccountRepository;
    private final VendorRepository vendorRepository;
    private final InvoiceRepository invoiceRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
        UserAccountRepository userAccountRepository,
        VendorRepository vendorRepository,
        InvoiceRepository invoiceRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.vendorRepository = vendorRepository;
        this.invoiceRepository = invoiceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking and seeding demonstration data for InvoiceIQ...");

        // 1. Provision Users
        String encodedPassword = passwordEncoder.encode("Password123!");
        UserAccount admin = ensureUser("admin@invoiceiq.com", encodedPassword, "Alex Morgan (Admin)", UserRole.ROLE_ADMIN);
        UserAccount employee = ensureUser("employee@invoiceiq.com", encodedPassword, "Sarah Chen (Employee)", UserRole.ROLE_EMPLOYEE);

        // 2. Provision Vendors
        Map<String, Vendor> vendorMap = new HashMap<>();
        List<Vendor> existingVendors = vendorRepository.findAll();
        for (Vendor v : existingVendors) {
            vendorMap.put(v.getName(), v);
        }

        Vendor aws = getOrCreateVendor(vendorMap, "Amazon Web Services (AWS)", "billing@aws.amazon.com", "Cloud Infrastructure", "29AABCA1234F1Z5");
        Vendor gcp = getOrCreateVendor(vendorMap, "Google Cloud Platform", "billing@google.com", "Cloud Infrastructure", "07AABCG5678K1ZQ");
        Vendor github = getOrCreateVendor(vendorMap, "GitHub Inc.", "billing@github.com", "Software Subscriptions", "29AABCG7890M1Z8");
        Vendor slack = getOrCreateVendor(vendorMap, "Slack Technologies", "ar@slack.com", "Communication", "27AABCS3456L1Z2");
        Vendor dell = getOrCreateVendor(vendorMap, "Dell Technologies", "enterprise-orders@dell.com", "Hardware & Equipment", "29AABCD1111P1Z1");
        Vendor wework = getOrCreateVendor(vendorMap, "WeWork Workspaces", "accounts@wework.co.in", "Office & Facilities", "29AABCW6789O1Z9");

        // 3. Provision Invoices if none exist
        if (invoiceRepository.count() > 0) {
            log.info("{} invoices already exist. Skipping invoice seed.", invoiceRepository.count());
            return;
        }

        log.info("Seeding sample invoices with AI extraction metadata and line items...");
        LocalDate today = LocalDate.now();

        // 1. AWS Cloud Hosting (Approved)
        Invoice inv1 = createInvoice(aws, admin, "INV-AWS-2026-08", today.minusDays(15), today.plusDays(15),
            new BigDecimal("14500.00"), new BigDecimal("2610.00"), new BigDecimal("0.00"), new BigDecimal("17110.00"), InvoiceStatus.APPROVED);
        addLineItem(inv1, 1, "AWS EC2 & RDS Production Clusters", new BigDecimal("1.000"), new BigDecimal("14500.00"), new BigDecimal("2610.00"), new BigDecimal("0.00"), new BigDecimal("17110.00"));
        invoiceRepository.save(inv1);

        // 2. Google Cloud GKE (Verified)
        Invoice inv2 = createInvoice(gcp, employee, "INV-GCP-8849", today.minusDays(8), today.plusDays(22),
            new BigDecimal("6850.00"), new BigDecimal("1233.00"), new BigDecimal("0.00"), new BigDecimal("8083.00"), InvoiceStatus.VERIFIED);
        addLineItem(inv2, 1, "Google Kubernetes Engine Workloads", new BigDecimal("1.000"), new BigDecimal("6850.00"), new BigDecimal("1233.00"), new BigDecimal("0.00"), new BigDecimal("8083.00"));
        invoiceRepository.save(inv2);

        // 3. GitHub Enterprise (Needs Review)
        Invoice inv3 = createInvoice(github, employee, "INV-GH-11029", today.minusDays(2), today.plusDays(28),
            new BigDecimal("3500.00"), new BigDecimal("630.00"), new BigDecimal("0.00"), new BigDecimal("4130.00"), InvoiceStatus.NEEDS_REVIEW);
        addLineItem(inv3, 1, "GitHub Enterprise Cloud Seats (20 Seats)", new BigDecimal("20.000"), new BigDecimal("175.00"), new BigDecimal("630.00"), new BigDecimal("0.00"), new BigDecimal("4130.00"));
        invoiceRepository.save(inv3);

        // 4. Slack Business+ (Approved)
        Invoice inv4 = createInvoice(slack, admin, "INV-SLACK-4920", today.minusDays(20), today.minusDays(5),
            new BigDecimal("4200.00"), new BigDecimal("756.00"), new BigDecimal("0.00"), new BigDecimal("4956.00"), InvoiceStatus.APPROVED);
        addLineItem(inv4, 1, "Slack Business+ Annual Subscription", new BigDecimal("1.000"), new BigDecimal("4200.00"), new BigDecimal("756.00"), new BigDecimal("0.00"), new BigDecimal("4956.00"));
        invoiceRepository.save(inv4);

        // 5. Dell Workstation (Rejected - Admin clarification needed)
        Invoice inv5 = createInvoice(dell, employee, "INV-DELL-99201", today.minusDays(5), today.plusDays(25),
            new BigDecimal("24500.00"), new BigDecimal("4410.00"), new BigDecimal("500.00"), new BigDecimal("28410.00"), InvoiceStatus.REJECTED);
        inv5.setRejectionReason("Requires hardware purchase approval requisition number in memo notes.");
        addLineItem(inv5, 1, "Dell Precision 5570 Developer Laptop", new BigDecimal("1.000"), new BigDecimal("24500.00"), new BigDecimal("4410.00"), new BigDecimal("500.00"), new BigDecimal("28410.00"));
        invoiceRepository.save(inv5);

        // 6. WeWork Bangalore Office (Needs Review)
        Invoice inv6 = createInvoice(wework, admin, "INV-WW-2026-08", today.minusDays(1), today.plusDays(30),
            new BigDecimal("8500.00"), new BigDecimal("1530.00"), new BigDecimal("0.00"), new BigDecimal("10030.00"), InvoiceStatus.NEEDS_REVIEW);
        addLineItem(inv6, 1, "Private Office Suite Monthly Retainer", new BigDecimal("1.000"), new BigDecimal("8500.00"), new BigDecimal("1530.00"), new BigDecimal("0.00"), new BigDecimal("10030.00"));
        invoiceRepository.save(inv6);

        log.info("Demo data successfully seeded: 2 Users, 6 Vendors, 6 Invoices.");
    }

    private UserAccount ensureUser(String email, String encodedPassword, String fullName, UserRole role) {
        return userAccountRepository.findByEmailIgnoreCase(email).map(existing -> {
            existing.setPasswordHash(encodedPassword);
            existing.setRole(role);
            return userAccountRepository.save(existing);
        }).orElseGet(() -> {
            UserAccount newUser = new UserAccount(email, encodedPassword, fullName, role);
            return userAccountRepository.save(newUser);
        });
    }

    private Vendor getOrCreateVendor(Map<String, Vendor> vendorMap, String name, String email, String category, String gstin) {
        if (vendorMap.containsKey(name)) {
            return vendorMap.get(name);
        }
        Vendor v = new Vendor(name);
        v.setEmail(email);
        v.setCategory(category);
        v.setGstin(gstin);
        v.setAddress("Level 4, Innovation Park, Tech Boulevard");
        Vendor saved = vendorRepository.save(v);
        vendorMap.put(name, saved);
        return saved;
    }

    private Invoice createInvoice(
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
        Invoice invoice = new Invoice(submitter);
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
}
