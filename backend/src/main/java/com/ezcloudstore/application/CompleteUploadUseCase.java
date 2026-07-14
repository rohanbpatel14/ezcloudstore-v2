package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileStatus;
import com.ezcloudstore.domain.model.FileVersion;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.model.UploadNotCompletedException;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;

import java.time.Clock;
import java.time.Instant;

/**
 * Confirms a client-side S3 upload: verifies the object exists (HEAD),
 * then either activates a pending file or records a new version on an
 * active one. The S3 object is the source of truth for size and version.
 */
public class CompleteUploadUseCase {

    private final FileRepository files;
    private final FileStorage storage;
    private final Clock clock;

    public CompleteUploadUseCase(FileRepository files, FileStorage storage, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.clock = clock;
    }

    public StoredFile handle(OwnerId owner, FileId fileId) {
        StoredFile file = files.find(fileId)
                .filter(f -> f.isOwnedBy(owner))
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));

        FileStorage.StoredObject object = storage.head(StorageKey.from(file))
                .orElseThrow(() -> new UploadNotCompletedException(
                        "No uploaded object found for file " + fileId.value()));

        Instant now = clock.instant();
        if (file.status() == FileStatus.PENDING_UPLOAD) {
            file.activate(object.s3VersionId(), object.sizeBytes(), now);
        } else {
            if (file.currentS3VersionId().filter(object.s3VersionId()::equals).isPresent()) {
                throw new UploadNotCompletedException(
                        "No new object version uploaded for file " + fileId.value());
            }
            file.recordNewVersion(object.s3VersionId(), object.sizeBytes(), now);
        }

        files.save(file);
        files.saveVersion(new FileVersion(fileId, object.s3VersionId(), object.sizeBytes(), now));
        return file;
    }
}
