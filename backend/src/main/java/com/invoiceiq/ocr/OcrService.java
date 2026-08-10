package com.invoiceiq.ocr;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * OCR abstraction. {@link TesseractOcrService} is the only implementation —
 * it shells out to the system `tesseract` binary (installed in the Docker
 * image) and reports itself unavailable when that binary isn't on PATH,
 * rather than faking a result. Callers must check {@link #isAvailable()}
 * and degrade gracefully (mark for manual review, don't crash) when false.
 */
public interface OcrService {

    boolean isAvailable();

    Optional<OcrResult> recognize(BufferedImage image);
}
