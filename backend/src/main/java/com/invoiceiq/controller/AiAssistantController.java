package com.invoiceiq.controller;

import com.invoiceiq.dto.AiAnswerResponse;
import com.invoiceiq.dto.AiQuestionRequest;
import com.invoiceiq.service.InvoiceAiService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class AiAssistantController {

    private final InvoiceAiService invoiceAiService;

    public AiAssistantController(InvoiceAiService invoiceAiService) {
        this.invoiceAiService = invoiceAiService;
    }

    @PostMapping("/{invoiceId}/ask")
    public AiAnswerResponse askQuestion(
        @PathVariable UUID invoiceId,
        @Valid @RequestBody AiQuestionRequest request
    ) {
        return invoiceAiService.ask(invoiceId, request.question());
    }
}
