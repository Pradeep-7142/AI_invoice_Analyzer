package com.invoiceiq.validation;

import java.util.List;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Deterministic, pre-AI file validation: empty files and disallowed types
 * are rejected before anything is stored or queued for processing.
 * Content type is sniffed from the actual bytes (Apache Tika), not trusted
 * from the browser-supplied Content-Type header or file extension.
 * Corruption/password-protection/document-classification checks are part
 * of the OCR/AI pipeline (Phase 4), not this stage.
 */
@Service
public class FileValidationService {

    private final Tika tika = new Tika();
    private final List<String> allowedContentTypes;

    public FileValidationService(@Value("${invoiceiq.upload.allowed-content-types}") String allowedContentTypes) {
        this.allowedContentTypes = List.of(allowedContentTypes.split(","));
    }

    /**
     * @return the actual sniffed content type, for callers to persist instead
     * of trusting the browser-supplied Content-Type header.
     */
    public String validate(byte[] content, String declaredFilename) {
        if (content.length == 0) {
            throw new InvalidFileException("The uploaded file is empty.");
        }

        String detectedType = tika.detect(content, declaredFilename);
        if (!allowedContentTypes.contains(detectedType)) {
            throw new InvalidFileException(
                "Unsupported file type \"" + detectedType + "\". Please upload a PDF, JPG, PNG, or WEBP file.");
        }
        return detectedType;
    }
}
