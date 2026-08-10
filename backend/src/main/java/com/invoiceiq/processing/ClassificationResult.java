package com.invoiceiq.processing;

import com.invoiceiq.entity.DocumentType;

record ClassificationResult(DocumentType documentType, double confidence) {
}
