package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;
import com.ezcloudstore.domain.port.IdGenerator;

import java.net.URI;
import java.time.Clock;

/**
 * Reserves file metadata in PENDING_UPLOAD and hands the client a presigned
 * PUT pinned to the declared size. Bytes go straight to S3 (ADR-0003);
 * CompleteUploadUseCase later verifies and activates.
 */
public class InitiateUploadUseCase {

    private final FileRepository files;
    private final FileStorage storage;
    private final IdGenerator ids;
    private final Clock clock;

    public InitiateUploadUseCase(FileRepository files, FileStorage storage, IdGenerator ids, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.ids = ids;
        this.clock = clock;
    }

    public UploadTicket handle(OwnerId owner, String name, String description,
                               long declaredSizeBytes, String contentType) {
        FileId id = ids.nextFileId();
        StoredFile file = StoredFile.create(id, owner, name, description,
                declaredSizeBytes, contentType, clock.instant());
        files.save(file);
        URI uploadUrl = storage.presignUpload(StorageKey.from(file), declaredSizeBytes);
        return new UploadTicket(id, uploadUrl);
    }
}
