package com.invoiceiq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.ai.AiProperties;
import com.invoiceiq.dto.AiAnswerResponse;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.repository.InvoiceRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class InvoiceAiService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAiService.class);

    private final InvoiceRepository invoiceRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public InvoiceAiService(
        InvoiceRepository invoiceRepository,
        AiProperties aiProperties,
        ObjectMapper objectMapper,
        RestClient.Builder restClientBuilder
    ) {
        this.invoiceRepository = invoiceRepository;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Transactional(readOnly = true)
    public AiAnswerResponse ask(UUID invoiceId, String question) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        String context = buildInvoiceContext(invoice);

        if (aiProperties.isLlmEnabled()) {
            try {
                return callLlmAssistant(question, context);
            } catch (Exception e) {
                log.warn("LLM question answering failed: {}. Falling back to rule-based assistant.", e.getMessage());
            }
        }

        return generateRuleBasedAnswer(invoice, question);
    }

    private String buildInvoiceContext(Invoice invoice) {
        StringBuilder sb = new StringBuilder();
        sb.append("Invoice Details:\n");
        sb.append("- Invoice Number: ").append(invoice.getInvoiceNumber()).append("\n");
        sb.append("- Vendor: ").append(invoice.getVendor() != null ? invoice.getVendor().getName() : invoice.getVendorNameRaw()).append("\n");
        sb.append("- Invoice Date: ").append(invoice.getInvoiceDate()).append("\n");
        sb.append("- Due Date: ").append(invoice.getDueDate()).append("\n");
        sb.append("- Currency: ").append(invoice.getCurrency()).append("\n");
        sb.append("- Subtotal: ").append(invoice.getSubtotalAmount()).append("\n");
        sb.append("- Tax Amount: ").append(invoice.getTaxAmount()).append("\n");
        sb.append("- Total Amount: ").append(invoice.getTotalAmount()).append("\n");
        sb.append("- Status: ").append(invoice.getStatus()).append("\n");

        if (invoice.getLineItems() != null && !invoice.getLineItems().isEmpty()) {
            sb.append("Line Items:\n");
            for (var item : invoice.getLineItems()) {
                sb.append(String.format("  * %s (Qty: %s, Unit: %s, Tax: %s, Total: %s)\n",
                    item.getDescription(), item.getQuantity(), item.getUnitPrice(), item.getTaxAmount(), item.getTotalAmount()));
            }
        }

        if (invoice.getDocuments() != null && !invoice.getDocuments().isEmpty()) {
            var doc = invoice.getDocuments().get(0);
            if (doc.getExtractedText() != null && !doc.getExtractedText().isBlank()) {
                sb.append("\nExtracted Document Raw Text:\n").append(doc.getExtractedText()).append("\n");
            }
        }

        return sb.toString();
    }

    private AiAnswerResponse callLlmAssistant(String question, String context) throws Exception {
        String endpoint = resolveEndpoint();
        String model = aiProperties.getModel() != null && !aiProperties.getModel().isBlank()
            ? aiProperties.getModel()
            : "gpt-4o-mini";

        String prompt = "You are InvoiceIQ Assistant, an AI specialized in analyzing financial invoices and documents.\n"
            + "Answer the user's question clearly, concisely, and accurately based ONLY on the provided invoice context.\n\n"
            + "Context:\n" + context;

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "temperature", 0.2,
            "messages", List.of(
                Map.of("role", "system", "content", prompt),
                Map.of("role", "user", "content", question)
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
        String answer = root.path("choices").get(0).path("message").path("content").asText();
        return new AiAnswerResponse(answer, "LLM (" + model + ")");
    }

    private AiAnswerResponse generateRuleBasedAnswer(Invoice invoice, String question) {
        String q = question.toLowerCase();
        String vendorName = invoice.getVendor() != null ? invoice.getVendor().getName() : (invoice.getVendorNameRaw() != null ? invoice.getVendorNameRaw() : "the vendor");

        if (q.contains("total") || q.contains("amount") || q.contains("cost") || q.contains("price") || q.contains("how much")) {
            return new AiAnswerResponse(
                String.format("The total amount for invoice %s is %s %s (Subtotal: %s, Tax: %s).",
                    invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                    invoice.getCurrency(),
                    invoice.getTotalAmount() != null ? invoice.getTotalAmount() : "0.00",
                    invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount() : "0.00",
                    invoice.getTaxAmount() != null ? invoice.getTaxAmount() : "0.00"),
                "Rule-Based Assistant"
            );
        }

        if (q.contains("due") || q.contains("date") || q.contains("when")) {
            return new AiAnswerResponse(
                String.format("This invoice is dated %s and is due on %s.",
                    invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : "Not specified",
                    invoice.getDueDate() != null ? invoice.getDueDate() : "Not specified"),
                "Rule-Based Assistant"
            );
        }

        if (q.contains("vendor") || q.contains("who") || q.contains("merchant") || q.contains("company")) {
            return new AiAnswerResponse(
                String.format("This invoice was issued by %s. Current status is %s.", vendorName, invoice.getStatus()),
                "Rule-Based Assistant"
            );
        }

        if (q.contains("tax") || q.contains("gst") || q.contains("vat")) {
            return new AiAnswerResponse(
                String.format("The tax recorded is %s %s on a subtotal of %s %s.",
                    invoice.getCurrency(),
                    invoice.getTaxAmount() != null ? invoice.getTaxAmount() : "0.00",
                    invoice.getCurrency(),
                    invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount() : "0.00"),
                "Rule-Based Assistant"
            );
        }

        if (q.contains("status") || q.contains("approved") || q.contains("verify") || q.contains("verified")) {
            return new AiAnswerResponse(
                String.format("Invoice %s is currently in '%s' status.",
                    invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                    invoice.getStatus()),
                "Rule-Based Assistant"
            );
        }

        return new AiAnswerResponse(
            String.format("Invoice %s from %s for %s %s is currently '%s'. Date: %s, Due: %s.",
                invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "N/A",
                vendorName,
                invoice.getCurrency(),
                invoice.getTotalAmount() != null ? invoice.getTotalAmount() : "0.00",
                invoice.getStatus(),
                invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : "N/A",
                invoice.getDueDate() != null ? invoice.getDueDate() : "N/A"),
            "Rule-Based Assistant"
        );
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
}
