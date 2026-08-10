package com.invoiceiq.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalDiskStorageService implements StorageService {

    private final Path rootPath;

    public LocalDiskStorageService(@Value("${invoiceiq.storage.root-path}") String rootPath) {
        this.rootPath = Path.of(rootPath);
    }

    @Override
    public StoredFile store(UUID organizationId, UUID invoiceId, String originalFilename, byte[] content) {
        String sanitizedFilename = sanitize(originalFilename);
        LocalDate today = LocalDate.now();

        Path directory = rootPath
            .resolve(organizationId.toString())
            .resolve(String.valueOf(today.getYear()))
            .resolve(String.format("%02d", today.getMonthValue()))
            .resolve(invoiceId.toString());

        String storedFilename = UUID.randomUUID() + "-" + sanitizedFilename;
        Path targetFile = directory.resolve(storedFilename);

        try {
            Files.createDirectories(directory);
            Files.write(targetFile, content);
        } catch (IOException e) {
            throw new StorageException("Failed to store uploaded document.", e);
        }

        String storageKey = rootPath.relativize(targetFile).toString();
        return new StoredFile(storageKey, content.length, sha256Hex(content));
    }

    @Override
    public InputStream retrieve(String storageKey) {
        Path file = rootPath.resolve(storageKey).normalize();
        if (!file.startsWith(rootPath)) {
            throw new StorageException("Invalid storage key.", null);
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new StorageException("Failed to read stored document.", e);
        }
    }

    private String sanitize(String filename) {
        String basename = Path.of(filename).getFileName().toString();
        return basename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
