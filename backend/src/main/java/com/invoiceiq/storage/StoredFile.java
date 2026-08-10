package com.invoiceiq.storage;

/**
 * Result of persisting an uploaded file. storageKey is an internal
 * reference only — it is never returned to API clients directly; documents
 * are always served back through an authenticated, tenant-checked endpoint.
 */
public record StoredFile(String storageKey, long sizeBytes, String checksumSha256) {
}
