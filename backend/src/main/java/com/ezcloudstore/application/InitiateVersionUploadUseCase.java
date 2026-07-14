package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileStatus;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.model.UploadNotPendingException;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;

import java.net.URI;

/**
 * Presigns a PUT for a new version of an existing active file (same storage
 * key — S3 versioning captures history). Completion goes through
 * CompleteUploadUseCase, which records the new version.
 */
public class InitiateVersionUploadUseCase {

    private final FileRepository files;
    private final FileStorage storage;

    public InitiateVersionUploadUseCase(FileRepository files, FileStorage storage) {
        this.files = files;
        this.storage = storage;
    }

    public UploadTicket handle(OwnerId owner, FileId fileId, long declaredSizeBytes) {
        StoredFile file = files.find(fileId)
                .filter(f -> f.isOwnedBy(owner))
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));
        if (file.status() != FileStatus.ACTIVE) {
            throw new UploadNotPendingException(
                    "File " + fileId.value() + " must be active to accept new versions");
        }
        StoredFile.requireValidSize(declaredSizeBytes);
        URI uploadUrl = storage.presignUpload(StorageKey.from(file), declaredSizeBytes);
        return new UploadTicket(fileId, uploadUrl);
    }
}
