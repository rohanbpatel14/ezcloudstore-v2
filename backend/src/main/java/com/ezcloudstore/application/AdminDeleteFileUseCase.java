package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;
import com.ezcloudstore.domain.port.ShareLinkRepository;

/**
 * Same teardown as DeleteFileUseCase but without the ownership filter.
 * Caller authorization (Cognito `admin` group) is enforced at the REST adapter.
 */
public class AdminDeleteFileUseCase {

    private final FileRepository files;
    private final ShareLinkRepository shareLinks;
    private final FileStorage storage;

    public AdminDeleteFileUseCase(FileRepository files, ShareLinkRepository shareLinks, FileStorage storage) {
        this.files = files;
        this.shareLinks = shareLinks;
        this.storage = storage;
    }

    public void handle(FileId fileId) {
        StoredFile file = files.find(fileId)
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));

        storage.deleteAllVersions(StorageKey.from(file));
        shareLinks.deleteAllForFile(fileId);
        files.deleteVersions(fileId);
        files.delete(fileId);
    }
}
