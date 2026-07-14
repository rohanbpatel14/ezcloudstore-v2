package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;
import com.ezcloudstore.domain.port.ShareLinkRepository;

public class DeleteFileUseCase {

    private final FileRepository files;
    private final ShareLinkRepository shareLinks;
    private final FileStorage storage;

    public DeleteFileUseCase(FileRepository files, ShareLinkRepository shareLinks, FileStorage storage) {
        this.files = files;
        this.shareLinks = shareLinks;
        this.storage = storage;
    }

    public void handle(OwnerId owner, FileId fileId) {
        StoredFile file = files.find(fileId)
                .filter(f -> f.isOwnedBy(owner))
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));

        storage.deleteAllVersions(StorageKey.from(file));
        shareLinks.deleteAllForFile(fileId);
        files.deleteVersions(fileId);
        files.delete(fileId);
    }
}
