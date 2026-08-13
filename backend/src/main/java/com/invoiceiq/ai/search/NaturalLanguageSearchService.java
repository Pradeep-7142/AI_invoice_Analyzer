package com.invoiceiq.ai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.ai.AiProperties;
import com.invoiceiq.dto.AiIntelligenceDto.NaturalSearchCriteria;
import com.invoiceiq.dto.AiIntelligenceDto.NaturalSearchRequest;
import com.invoiceiq.dto.AiIntelligenceDto.NaturalSearchResponse;
import com.invoiceiq.dto.InvoiceSummaryResponse;
import com.invoiceiq.dto.VendorSummaryDto;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class NaturalLanguageSearchService {

    private static final Logger log = LoggerFactory.getLogger(NaturalLanguageSearchService.class);

    private final InvoiceRepository invoiceRepository;
    private final CurrentUser currentUser;
    private final AiProperties aiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NaturalLanguageSearchService(
        InvoiceRepository invoiceRepository,
        CurrentUser currentUser,
        AiProperties aiProperties,
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper
    ) {
        this.invoiceRepository = invoiceRepository;
        this.currentUser = currentUser;
        this.aiProperties = aiProperties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public NaturalSearchResponse search(NaturalSearchRequest request) {
        String query = request.query() == null ? "" : request.query().trim();
        NaturalSearchCriteria criteria;

        if (aiProperties.isLlmEnabled() && !query.isBlank()) {
            try {
                criteria = parseWithLlm(query);
            } catch (Exception e) {
                log.warn("LLM search parsing failed ({}). Falling back to heuristic query parser.", e.getMessage());
                criteria = parseWithHeuristics(query);
            }
        } else {
            criteria = parseWithHeuristics(query);
        }

        List<InvoiceSummaryResponse> results = executeFilteredQuery(criteria);
        return new NaturalSearchResponse(query, criteria, results, results.size());
    }

    private List<InvoiceSummaryResponse> executeFilteredQuery(NaturalSearchCriteria criteria) {
        UUID organizationId = currentUser.organizationId();
        List<Invoice> all = invoiceRepository.findByOrganizationId(organizationId);

        return all.stream()
            .filter(i -> {
                // Employee scoping: only own submissions
                if (currentUser.role() == OrgRole.EMPLOYEE) {
                    if (!i.getSubmittedBy().getId().equals(currentUser.userId())) {
                        return false;
                    }
                }
                // Status match
                if (criteria.status() != null && i.getStatus() != criteria.status()) {
                    return false;
                }
                // Amount range
                if (criteria.minAmount() != null) {
                    if (i.getTotalAmount() == null || i.getTotalAmount().compareTo(criteria.minAmount()) < 0) {
                        return false;
                    }
                }
                if (criteria.maxAmount() != null) {
                    if (i.getTotalAmount() == null || i.getTotalAmount().compareTo(criteria.maxAmount()) > 0) {
                        return false;
                    }
                }
                // Vendor match
                if (criteria.vendorName() != null && !criteria.vendorName().isBlank()) {
                    String vName = i.getVendor() != null ? i.getVendor().getName() : i.getVendorNameRaw();
                    if (vName == null || !vName.toLowerCase().contains(criteria.vendorName().toLowerCase())) {
                        return false;
                    }
                }
                // Category match
                if (criteria.category() != null && !criteria.category().isBlank()) {
                    if (i.getVendor() == null || i.getVendor().getCategory() == null ||
                        !i.getVendor().getCategory().toLowerCase().contains(criteria.category().toLowerCase())) {
                        return false;
                    }
                }
                // Date range
                if (criteria.fromDate() != null && i.getInvoiceDate() != null) {
                    if (i.getInvoiceDate().isBefore(criteria.fromDate())) {
                        return false;
                    }
                }
                if (criteria.toDate() != null && i.getInvoiceDate() != null) {
                    if (i.getInvoiceDate().isAfter(criteria.toDate())) {
                        return false;
                    }
                }
                return true;
            })
            .map(this::toSummaryResponse)
            .toList();
    }

    private NaturalSearchCriteria parseWithHeuristics(String query) {
        String lower = query.toLowerCase();
        BigDecimal minAmount = null;
        BigDecimal maxAmount = null;
        InvoiceStatus status = null;
        LocalDate fromDate = null;
        LocalDate toDate = LocalDate.now();
        String vendorName = null;
        String category = null;

        // Extract numbers / amounts like "over 50000" or "> 10000"
        Pattern overPattern = Pattern.compile("(?i)(?:over|greater than|above|>|more than)\\s*[₹$]?\\s*([\\d,]+)");
        Matcher overMatcher = overPattern.matcher(query);
        if (overMatcher.find()) {
            minAmount = parseAmount(overMatcher.group(1));
        }

        Pattern underPattern = Pattern.compile("(?i)(?:under|less than|below|<)\\s*[₹$]?\\s*([\\d,]+)");
        Matcher underMatcher = underPattern.matcher(query);
        if (underMatcher.find()) {
            maxAmount = parseAmount(underMatcher.group(1));
        }

        // Status keywords
        if (lower.contains("overdue")) {
            status = InvoiceStatus.OVERDUE;
        } else if (lower.contains("approved")) {
            status = InvoiceStatus.APPROVED;
        } else if (lower.contains("pending approval") || lower.contains("pending")) {
            status = InvoiceStatus.PENDING_APPROVAL;
        } else if (lower.contains("needs review") || lower.contains("review")) {
            status = InvoiceStatus.NEEDS_REVIEW;
        } else if (lower.contains("paid")) {
            status = InvoiceStatus.PAID;
        } else if (lower.contains("disputed")) {
            status = InvoiceStatus.DISPUTED;
        }

        // Timeframe
        if (lower.contains("last month") || lower.contains("past month")) {
            fromDate = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        } else if (lower.contains("last 3 months") || lower.contains("last quarter")) {
            fromDate = LocalDate.now().minusMonths(3);
        } else if (lower.contains("last 6 months")) {
            fromDate = LocalDate.now().minusMonths(6);
        } else if (lower.contains("this year")) {
            fromDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        }

        // Known common categories
        for (String cat : List.of("Software", "Cloud", "Marketing", "Travel", "Office", "Utilities", "Professional Services", "Equipment")) {
            if (lower.contains(cat.toLowerCase())) {
                category = cat;
                break;
            }
        }

        StringBuilder interp = new StringBuilder("Interpreted criteria: ");
        if (status != null) interp.append("Status = ").append(status).append(", ");
        if (minAmount != null) interp.append("Min Amount = ₹").append(minAmount).append(", ");
        if (maxAmount != null) interp.append("Max Amount = ₹").append(maxAmount).append(", ");
        if (category != null) interp.append("Category = ").append(category).append(", ");
        if (fromDate != null) interp.append("From = ").append(fromDate).append(", ");

        String finalInterp = interp.toString().replaceAll(", $", "");
        if (finalInterp.equals("Interpreted criteria: ")) {
            finalInterp = "Searching all invoices matching text.";
        }

        return new NaturalSearchCriteria(vendorName, category, status, minAmount, maxAmount, fromDate, toDate, finalInterp);
    }

    private NaturalSearchCriteria parseWithLlm(String query) throws Exception {
        String systemPrompt = """
            You are a natural language query interpreter for InvoiceIQ financial search.
            Translate the user's plain English search request into a JSON structured filter with this exact schema:
            {
              "vendorName": string or null,
              "category": string or null,
              "status": "NEEDS_REVIEW" | "VERIFIED" | "PENDING_APPROVAL" | "APPROVED" | "PAYMENT_SCHEDULED" | "PARTIALLY_PAID" | "PAID" | "OVERDUE" | "DISPUTED" | "ARCHIVED" | null,
              "minAmount": number or null,
              "maxAmount": number or null,
              "fromDate": "YYYY-MM-DD" or null,
              "toDate": "YYYY-MM-DD" or null,
              "interpretation": "Short human-readable summary of the applied filters"
            }
            Reference Date Today: """ + LocalDate.now().toString() + """
            Return ONLY raw JSON with NO markdown formatting.
            """;

        String endpoint = resolveEndpoint();
        Map<String, Object> requestBody = Map.of(
            "model", aiProperties.getModel() != null ? aiProperties.getModel() : "gpt-4o-mini",
            "temperature", 0.0,
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", query)
            )
        );

        String rawResponse = restClient.post()
            .uri(endpoint)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
            .header(HttpHeaders.USER_AGENT, "InvoiceIQ/1.0")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(String.class);

        JsonNode root = objectMapper.readTree(rawResponse);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        JsonNode data = objectMapper.readTree(content.trim());

        String vendorName = data.hasNonNull("vendorName") ? data.get("vendorName").asText() : null;
        String category = data.hasNonNull("category") ? data.get("category").asText() : null;
        InvoiceStatus status = null;
        if (data.hasNonNull("status")) {
            try {
                status = InvoiceStatus.valueOf(data.get("status").asText().toUpperCase());
            } catch (Exception ignored) {}
        }
        BigDecimal minAmount = data.hasNonNull("minAmount") ? BigDecimal.valueOf(data.get("minAmount").asDouble()) : null;
        BigDecimal maxAmount = data.hasNonNull("maxAmount") ? BigDecimal.valueOf(data.get("maxAmount").asDouble()) : null;
        LocalDate fromDate = parseDate(data.path("fromDate").asText(null));
        LocalDate toDate = parseDate(data.path("toDate").asText(null));
        String interp = data.path("interpretation").asText("Natural search filter applied.");

        return new NaturalSearchCriteria(vendorName, category, status, minAmount, maxAmount, fromDate, toDate, interp);
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private BigDecimal parseAmount(String raw) {
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveEndpoint() {
        String base = aiProperties.getBaseUrl();
        if (base != null && !base.isBlank()) {
            return base.endsWith("/chat/completions") ? base : base + (base.endsWith("/") ? "chat/completions" : "/chat/completions");
        }
        if ("gemini".equalsIgnoreCase(aiProperties.getProvider())) {
            return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
        }
        if ("ollama".equalsIgnoreCase(aiProperties.getProvider())) {
            return "http://localhost:11434/v1/chat/completions";
        }
        return "https://api.openai.com/v1/chat/completions";
    }

    private InvoiceSummaryResponse toSummaryResponse(Invoice invoice) {
        Vendor vendor = invoice.getVendor();
        VendorSummaryDto vendorSummary = vendor != null ? new VendorSummaryDto(vendor.getId(), vendor.getName()) : null;
        return new InvoiceSummaryResponse(
            invoice.getId(),
            vendorSummary,
            invoice.getInvoiceNumber(),
            invoice.getInvoiceDate(),
            invoice.getDueDate(),
            invoice.getCurrency(),
            invoice.getTotalAmount(),
            invoice.getStatus(),
            invoice.getSubmittedBy().getFullName(),
            invoice.getCreatedAt()
        );
    }
}
