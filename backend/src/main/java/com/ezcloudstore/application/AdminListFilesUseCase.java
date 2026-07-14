package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.port.FileRepository;

import java.util.List;

/**
 * Caller authorization (Cognito `admin` group) is enforced at the REST
 * adapter; this use case only expresses the capability.
 */
public class AdminListFilesUseCase {

    private final FileRepository files;

    public AdminListFilesUseCase(FileRepository files) {
        this.files = files;
    }

    public List<StoredFile> handle() {
        return files.listAll();
    }
}
