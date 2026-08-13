package com.invoiceiq.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Production-ready LLM invoice extraction implementation supporting OpenAI,
 * Gemini, Ollama, and OpenAI-compatible gateways. If the LLM provider is set to
 * 'mock', no API key is present, or the network request fails, it automatically
 * falls back to {@link MockAiExtractionService} so extraction always succeeds.
 */
@Service
@Primary
public class LlmExtractionService implements AiExtractionService {

    private static final Logger log = LoggerFactory.getLogger(LlmExtractionService.class);

    private static final String SYSTEM_PROMPT = """
        You are an expert financial document intelligence AI.
        Extract the following structured fields from the provided invoice/receipt text:
        - vendorNameRaw: The merchant or vendor name issuing the invoice.
        - invoiceNumber: The unique invoice or bill identifier.
        - invoiceDate: The date of invoice issuance in ISO format (YYYY-MM-DD).
        - dueDate: The payment due date in ISO format (YYYY-MM-DD).
        - subtotalAmount: Net amount before tax as a decimal number.
        - taxAmount: Total tax / GST amount as a decimal number.
        - totalAmount: Final gross invoice total amount as a decimal number.
        - gstin: Indian GSTIN (if applicable/present).
        - confidences: An object with a float between 0.0 and 1.0 for each extracted field representing your certainty.

        Return ONLY a raw JSON object conforming strictly to this schema with NO markdown code block wrappers:
        {
          "vendorNameRaw": "Acme Corp",
          "invoiceNumber": "INV-1002",
          "invoiceDate": "2026-08-01",
          "dueDate": "2026-08-15",
          "subtotalAmount": 1000.00,
          "taxAmount": 180.00,
          "totalAmount": 1180.00,
          "gstin": "29ABCDE1234F1Z5",
          "confidences": {
            "vendorName": 0.95,
            "invoiceNumber": 0.98,
            "invoiceDate": 0.95,
            "dueDate": 0.90,
            "subtotalAmount": 0.95,
            "taxAmount": 0.95,
            "totalAmount": 0.99,
            "gstin": 0.95
          }
        }
        If any field is absent from the document, set its value to null.
        """;

    private final AiProperties aiProperties;
    private final MockAiExtractionService mockAiExtractionService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LlmExtractionService(
        AiProperties aiProperties,
        MockAiExtractionService mockAiExtractionService,
        ObjectMapper objectMapper,
        RestClient.Builder restClientBuilder
    ) {
        this.aiProperties = aiProperties;
        this.mockAiExtractionService = mockAiExtractionService;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public ExtractedInvoiceFields extract(String text) {
        if (!aiProperties.isLlmEnabled() || text == null || text.isBlank()) {
            return mockAiExtractionService.extract(text);
        }

        try {
            return callLlm(text);
        } catch (Exception e) {
            log.warn("LLM extraction failed or timed out ({}). Falling back to heuristic extractor.", e.getMessage());
            return mockAiExtractionService.extract(text);
        }
    }

    private ExtractedInvoiceFields callLlm(String text) throws Exception {
        String endpoint = resolveEndpoint();
        String model = aiProperties.getModel() != null && !aiProperties.getModel().isBlank()
            ? aiProperties.getModel()
            : "gpt-4o-mini";

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "temperature", 0.0,
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", "Extract invoice data from this document text:\n\n" + text)
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

        return parseLlmResponse(rawResponse);
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

    private ExtractedInvoiceFields parseLlmResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) {
            throw new IllegalStateException("Empty choices in LLM completion response");
        }

        String content = choices.get(0).path("message").path("content").asText();
        // Clean markdown backticks if present
        String cleanJson = content.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        JsonNode data = objectMapper.readTree(cleanJson);

        String vendorNameRaw = data.hasNonNull("vendorNameRaw") ? data.get("vendorNameRaw").asText() : null;
        String invoiceNumber = data.hasNonNull("invoiceNumber") ? data.get("invoiceNumber").asText() : null;
        LocalDate invoiceDate = parseDateSafe(data.path("invoiceDate").asText(null));
        LocalDate dueDate = parseDateSafe(data.path("dueDate").asText(null));
        BigDecimal subtotalAmount = parseDecimalSafe(data.path("subtotalAmount"));
        BigDecimal taxAmount = parseDecimalSafe(data.path("taxAmount"));
        BigDecimal totalAmount = parseDecimalSafe(data.path("totalAmount"));
        String gstin = data.hasNonNull("gstin") ? data.get("gstin").asText() : null;

        Map<String, Double> confidences = new HashMap<>();
        JsonNode confNode = data.path("confidences");
        if (confNode.isObject()) {
            confNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isNumber()) {
                    confidences.put(entry.getKey(), entry.getValue().asDouble());
                }
            });
        }

        return new ExtractedInvoiceFields(
            vendorNameRaw, invoiceNumber, invoiceDate, dueDate,
            subtotalAmount, taxAmount, totalAmount, gstin, confidences
        );
    }

    private LocalDate parseDateSafe(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private BigDecimal parseDecimalSafe(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return BigDecimal.valueOf(node.asDouble());
            }
            String val = node.asText().replace(",", "").trim();
            return val.isBlank() ? null : new BigDecimal(val);
        } catch (Exception e) {
            return null;
        }
    }
}
