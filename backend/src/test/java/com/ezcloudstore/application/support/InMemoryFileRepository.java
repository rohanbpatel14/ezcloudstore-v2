package com.ezcloudstore.application.support;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileVersion;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.port.FileRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryFileRepository implements FileRepository {

    private final Map<FileId, StoredFile> files = new LinkedHashMap<>();
    private final Map<FileId, List<FileVersion>> versions = new LinkedHashMap<>();

    @Override
    public void save(StoredFile file) {
        files.put(file.id(), file);
    }

    @Override
    public Optional<StoredFile> find(FileId id) {
        return Optional.ofNullable(files.get(id));
    }

    @Override
    public List<StoredFile> listByOwner(OwnerId owner) {
        return files.values().stream().filter(f -> f.isOwnedBy(owner)).toList();
    }

    @Override
    public List<StoredFile> listAll() {
        return List.copyOf(files.values());
    }

    @Override
    public void delete(FileId id) {
        files.remove(id);
    }

    @Override
    public void saveVersion(FileVersion version) {
        versions.computeIfAbsent(version.fileId(), k -> new ArrayList<>()).add(version);
    }

    @Override
    public List<FileVersion> listVersions(FileId id) {
        return List.copyOf(versions.getOrDefault(id, List.of()));
    }

    @Override
    public void deleteVersions(FileId id) {
        versions.remove(id);
    }
}
