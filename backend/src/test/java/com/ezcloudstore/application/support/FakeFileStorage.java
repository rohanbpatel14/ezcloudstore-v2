package com.ezcloudstore.application.support;

import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.port.FileStorage;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeFileStorage implements FileStorage {

    public record PresignUploadCall(StorageKey key, long contentLengthBytes) {
    }

    public record PresignDownloadCall(StorageKey key, String s3VersionId, String downloadFileName) {
    }

    public final List<PresignUploadCall> uploadCalls = new ArrayList<>();
    public final List<PresignDownloadCall> downloadCalls = new ArrayList<>();
    public final List<StorageKey> deletedKeys = new ArrayList<>();

    private final Map<String, StoredObject> objects = new HashMap<>();

    public void clear() {
        uploadCalls.clear();
        downloadCalls.clear();
        deletedKeys.clear();
        objects.clear();
    }

    public void putObject(StorageKey key, StoredObject object) {
        objects.put(key.value(), object);
    }

    @Override
    public URI presignUpload(StorageKey key, long contentLengthBytes) {
        uploadCalls.add(new PresignUploadCall(key, contentLengthBytes));
        return URI.create("https://s3.fake/upload/" + key.value());
    }

    @Override
    public URI presignDownload(StorageKey key, String s3VersionId, String downloadFileName) {
        downloadCalls.add(new PresignDownloadCall(key, s3VersionId, downloadFileName));
        String version = s3VersionId == null ? "current" : s3VersionId;
        return URI.create("https://s3.fake/download/" + key.value() + "?versionId=" + version);
    }

    @Override
    public Optional<StoredObject> head(StorageKey key) {
        return Optional.ofNullable(objects.get(key.value()));
    }

    @Override
    public void deleteAllVersions(StorageKey key) {
        deletedKeys.add(key);
        objects.remove(key.value());
    }
}
