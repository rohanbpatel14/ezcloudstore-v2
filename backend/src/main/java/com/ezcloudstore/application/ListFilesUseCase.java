package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.port.FileRepository;

import java.util.List;

public class ListFilesUseCase {

    private final FileRepository files;

    public ListFilesUseCase(FileRepository files) {
        this.files = files;
    }

    public List<StoredFile> handle(OwnerId owner) {
        return files.listByOwner(owner);
    }
}
