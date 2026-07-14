package com.ezcloudstore.domain.port;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileVersion;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StoredFile;

import java.util.List;
import java.util.Optional;

public interface FileRepository {

    void save(StoredFile file);

    Optional<StoredFile> find(FileId id);

    List<StoredFile> listByOwner(OwnerId owner);

    List<StoredFile> listAll();

    void delete(FileId id);

    void saveVersion(FileVersion version);

    List<FileVersion> listVersions(FileId id);

    void deleteVersions(FileId id);
}
