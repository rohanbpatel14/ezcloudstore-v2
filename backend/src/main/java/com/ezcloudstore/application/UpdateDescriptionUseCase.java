package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.port.FileRepository;

import java.time.Clock;

public class UpdateDescriptionUseCase {

    private final FileRepository files;
    private final Clock clock;

    public UpdateDescriptionUseCase(FileRepository files, Clock clock) {
        this.files = files;
        this.clock = clock;
    }

    public StoredFile handle(OwnerId owner, FileId fileId, String description) {
        StoredFile file = files.find(fileId)
                .filter(f -> f.isOwnedBy(owner))
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));
        file.updateDescription(description, clock.instant());
        files.save(file);
        return file;
    }
}
