package com.ezcloudstore.adapters.out.id;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.port.IdGenerator;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * File ids are UUIDs (non-secret identifiers); share tokens are 256-bit
 * URL-safe secrets — the token IS the credential for public resolution.
 */
public class SecureRandomIdGenerator implements IdGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public FileId nextFileId() {
        return FileId.of(UUID.randomUUID().toString());
    }

    @Override
    public ShareToken nextShareToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return ShareToken.of(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
}
