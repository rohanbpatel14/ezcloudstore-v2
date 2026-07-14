package com.ezcloudstore.domain.port;

import com.ezcloudstore.domain.model.StorageKey;

import java.net.URI;
import java.util.Optional;

public interface FileStorage {

    /** Presigned PUT; the signature pins the exact content length so S3 rejects oversized bodies. */
    URI presignUpload(StorageKey key, long contentLengthBytes);

    /** Presigned GET for the given (or current, when null) object version. */
    URI presignDownload(StorageKey key, String s3VersionId, String downloadFileName);

    Optional<StoredObject> head(StorageKey key);

    void deleteAllVersions(StorageKey key);

    record StoredObject(String s3VersionId, long sizeBytes) {
    }
}
