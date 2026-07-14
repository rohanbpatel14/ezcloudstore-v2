package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.port.FileRepository;

public class GetFileUseCase {

    private final FileRepository files;

    public GetFileUseCase(FileRepository files) {
        this.files = files;
    }

    public FileDetails handle(OwnerId owner, FileId fileId) {
        StoredFile file = files.find(fileId)
                .filter(f -> f.isOwnedBy(owner))
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));
        return new FileDetails(file, files.listVersions(fileId));
    }
}
