package com.ezcloudstore.domain.model;

/**
 * Raised when a file does not exist or the caller may not see it —
 * deliberately the same signal for both, to prevent resource enumeration.
 */
public class StoredFileNotFoundException extends DomainException {

    public StoredFileNotFoundException(FileId id) {
        super("File not found: " + id.value());
    }
}
