package com.ezcloudstore.domain.model;

import java.time.Instant;

public record FileVersion(FileId fileId, String s3VersionId, long sizeBytes, Instant createdAt) {
}
