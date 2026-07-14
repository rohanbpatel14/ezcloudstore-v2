package com.ezcloudstore.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for a user's file. Lifecycle:
 * PENDING_UPLOAD (metadata reserved, presigned PUT issued)
 * → ACTIVE (upload confirmed against S3; versions may then accrue).
 */
public class StoredFile {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private final FileId id;
    private final OwnerId owner;
    private final String name;
    private final String contentType;
    private final Instant createdAt;

    private String description;
    private long sizeBytes;
    private FileStatus status;
    private Instant updatedAt;
    private String currentS3VersionId;

    private StoredFile(FileId id, OwnerId owner, String name, String description,
                       long sizeBytes, String contentType, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = name;
        this.description = description == null ? "" : description;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        this.status = FileStatus.PENDING_UPLOAD;
    }

    public static StoredFile create(FileId id, OwnerId owner, String name, String description,
                                    long sizeBytes, String contentType, Instant now) {
        if (name == null || name.isBlank()) {
            throw new InvalidFileNameException("File name must not be blank");
        }
        requireValidSize(sizeBytes);
        return new StoredFile(id, owner, name, description, sizeBytes, contentType, now);
    }

    /** Rehydrates persisted state without re-running creation invariants. Adapters only. */
    public static StoredFile restore(FileId id, OwnerId owner, String name, String description,
                                     long sizeBytes, String contentType, FileStatus status,
                                     Instant createdAt, Instant updatedAt, String currentS3VersionId) {
        StoredFile file = new StoredFile(id, owner, name, description, sizeBytes, contentType, createdAt);
        file.status = status;
        file.updatedAt = updatedAt;
        file.currentS3VersionId = currentS3VersionId;
        return file;
    }

    public static void requireValidSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new FileTooLargeException(
                    "File size must be between 1 byte and " + MAX_SIZE_BYTES + " bytes, was " + sizeBytes);
        }
    }

    public void activate(String s3VersionId, long actualSizeBytes, Instant now) {
        if (status != FileStatus.PENDING_UPLOAD) {
            throw new UploadNotPendingException("File " + id.value() + " is not awaiting an initial upload");
        }
        this.currentS3VersionId = s3VersionId;
        this.sizeBytes = actualSizeBytes;
        this.status = FileStatus.ACTIVE;
        this.updatedAt = now;
    }

    public void recordNewVersion(String s3VersionId, long sizeBytes, Instant now) {
        if (status != FileStatus.ACTIVE) {
            throw new UploadNotPendingException("File " + id.value() + " must be active to accept new versions");
        }
        this.currentS3VersionId = s3VersionId;
        this.sizeBytes = sizeBytes;
        this.updatedAt = now;
    }

    public void updateDescription(String description, Instant now) {
        this.description = description == null ? "" : description;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(OwnerId candidate) {
        return owner.equals(candidate);
    }

    public FileId id() {
        return id;
    }

    public OwnerId owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String contentType() {
        return contentType;
    }

    public FileStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Optional<String> currentS3VersionId() {
        return Optional.ofNullable(currentS3VersionId);
    }
}
