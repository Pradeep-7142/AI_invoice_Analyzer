package com.invoiceiq.ocr;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TesseractOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);
    private static final long TIMEOUT_SECONDS = 30;

    private final boolean available;

    public TesseractOcrService() {
        this.available = detectAvailability();
        if (!available) {
            log.warn("tesseract binary not found on PATH — OCR is disabled; scanned/image documents will be "
                + "marked NEEDS_REVIEW instead of having text extracted.");
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Optional<OcrResult> recognize(BufferedImage image) {
        if (!available) {
            return Optional.empty();
        }

        Path tempImage = null;
        try {
            tempImage = Files.createTempFile("invoiceiq-ocr-", ".png");
            ImageIO.write(image, "png", tempImage.toFile());

            Process process = new ProcessBuilder("tesseract", tempImage.toString(), "stdout", "tsv")
                .redirectErrorStream(false)
                .start();

            String tsv;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                tsv = reader.lines().reduce("", (a, b) -> a + b + "\n");
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("tesseract OCR timed out after {}s", TIMEOUT_SECONDS);
                return Optional.empty();
            }

            return Optional.of(parseTsv(tsv));
        } catch (IOException | InterruptedException e) {
            log.warn("tesseract OCR invocation failed: {}", e.getMessage());
            return Optional.empty();
        } finally {
            if (tempImage != null) {
                try {
                    Files.deleteIfExists(tempImage);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp scratch file
                }
            }
        }
    }

    private OcrResult parseTsv(String tsv) {
        List<String> words = new ArrayList<>();
        List<Double> confidences = new ArrayList<>();

        for (String line : tsv.split("\n")) {
            String[] fields = line.split("\t");
            if (fields.length < 12) {
                continue;
            }
            double confidence;
            try {
                confidence = Double.parseDouble(fields[10]);
            } catch (NumberFormatException e) {
                continue;
            }
            String word = fields[11].trim();
            if (confidence < 0 || word.isEmpty()) {
                continue;
            }
            words.add(word);
            confidences.add(confidence);
        }

        String text = String.join(" ", words);
        OptionalDouble average = confidences.stream().mapToDouble(Double::doubleValue).average();
        double normalizedConfidence = average.isPresent() ? average.getAsDouble() / 100.0 : 0.0;
        return new OcrResult(text, normalizedConfidence);
    }

    private boolean detectAvailability() {
        try {
            Process process = new ProcessBuilder("tesseract", "--version").start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
