package com.invoiceiq.storage;

import java.io.InputStream;
import java.util.UUID;

public interface StorageService {

    StoredFile store(UUID invoiceId, String originalFilename, byte[] content);

    InputStream retrieve(String storageKey);
}
