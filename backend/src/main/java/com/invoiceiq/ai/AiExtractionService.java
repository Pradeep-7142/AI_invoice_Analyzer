package com.invoiceiq.ai;

/**
 * AI/NLP extraction abstraction, selected via {@code invoiceiq.ai.provider}.
 * {@link MockAiExtractionService} is the only implementation today — a
 * deterministic, explainable regex/heuristic extractor, not a real language
 * model. It exists so the full pipeline (upload → extract → confidence
 * score → human verification) works end-to-end with zero external API
 * calls or cost; a real LLM-backed implementation can be added behind this
 * same interface later without touching the pipeline that calls it.
 */
public interface AiExtractionService {

    ExtractedInvoiceFields extract(String text);
}
