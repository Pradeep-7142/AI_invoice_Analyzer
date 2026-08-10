package com.invoiceiq.storage;

import java.io.InputStream;
import java.util.UUID;

/**
 * Document storage abstraction. {@link LocalDiskStorageService} is the only
 * implementation today (a Docker volume in production, a local folder in
 * dev); the interface is deliberately storage-agnostic so a later move to
 * S3/GCS/Azure Blob only requires a new implementation, not API changes.
 */
public interface StorageService {

    StoredFile store(UUID organizationId, UUID invoiceId, String originalFilename, byte[] content);

    InputStream retrieve(String storageKey);
}
