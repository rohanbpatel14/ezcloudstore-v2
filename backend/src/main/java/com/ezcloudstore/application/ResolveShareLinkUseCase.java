package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.ShareLinkExpiredException;
import com.ezcloudstore.domain.model.ShareLinkNotFoundException;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;
import com.ezcloudstore.domain.port.ShareLinkRepository;

import java.net.URI;
import java.time.Clock;

/**
 * The only unauthenticated read path: a valid, unexpired token resolves to a
 * short-lived presigned GET of the file's current version.
 */
public class ResolveShareLinkUseCase {

    private final ShareLinkRepository shareLinks;
    private final FileRepository files;
    private final FileStorage storage;
    private final Clock clock;

    public ResolveShareLinkUseCase(ShareLinkRepository shareLinks, FileRepository files,
                                   FileStorage storage, Clock clock) {
        this.shareLinks = shareLinks;
        this.files = files;
        this.storage = storage;
        this.clock = clock;
    }

    public URI handle(ShareToken token) {
        ShareLink link = shareLinks.find(token)
                .orElseThrow(() -> new ShareLinkNotFoundException(token));
        if (link.isExpired(clock.instant())) {
            throw new ShareLinkExpiredException(token);
        }
        StoredFile file = files.find(link.fileId())
                .orElseThrow(() -> new ShareLinkNotFoundException(token));
        return storage.presignDownload(StorageKey.from(file), null, file.name());
    }
}
