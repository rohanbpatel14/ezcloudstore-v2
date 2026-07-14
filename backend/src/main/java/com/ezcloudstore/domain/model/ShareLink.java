package com.ezcloudstore.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A revocable, expiring public link to one file. Expiry is enforced both
 * here (resolve-time check) and by DynamoDB TTL (storage-level cleanup).
 */
public class ShareLink {

    public static final Duration MAX_TTL = Duration.ofDays(7);

    private final ShareToken token;
    private final FileId fileId;
    private final OwnerId owner;
    private final Instant createdAt;
    private final Instant expiresAt;

    private ShareLink(ShareToken token, FileId fileId, OwnerId owner, Instant createdAt, Instant expiresAt) {
        this.token = Objects.requireNonNull(token, "token");
        this.fileId = Objects.requireNonNull(fileId, "fileId");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static ShareLink create(ShareToken token, FileId fileId, OwnerId owner,
                                   Instant now, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) {
            throw new InvalidShareTtlException(
                    "Share link TTL must be positive and at most " + MAX_TTL + ", was " + ttl);
        }
        return new ShareLink(token, fileId, owner, now, now.plus(ttl));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public ShareToken token() {
        return token;
    }

    public FileId fileId() {
        return fileId;
    }

    public OwnerId owner() {
        return owner;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
