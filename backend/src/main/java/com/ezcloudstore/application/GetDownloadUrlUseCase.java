package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileStatus;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.model.UploadNotCompletedException;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;

import java.net.URI;

public class GetDownloadUrlUseCase {

    private final FileRepository files;
    private final FileStorage storage;

    public GetDownloadUrlUseCase(FileRepository files, FileStorage storage) {
        this.files = files;
        this.storage = storage;
    }

    public URI handle(OwnerId owner, FileId fileId, String s3VersionId) {
        StoredFile file = files.find(fileId)
                .filter(f -> f.isOwnedBy(owner))
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));
        if (file.status() != FileStatus.ACTIVE) {
            throw new UploadNotCompletedException("File " + fileId.value() + " has no downloadable content yet");
        }
        return storage.presignDownload(StorageKey.from(file), s3VersionId, file.name());
    }
}
