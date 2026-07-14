package com.ezcloudstore.domain.model;

/**
 * S3 object key for a stored file: one key per file, namespaced by owner
 * (mirrors v1's per-user directory isolation); versions accrue on the key.
 */
public record StorageKey(OwnerId owner, FileId fileId) {

    public static StorageKey from(StoredFile file) {
        return new StorageKey(file.owner(), file.id());
    }

    public String value() {
        return owner.value() + "/" + fileId.value();
    }
}
