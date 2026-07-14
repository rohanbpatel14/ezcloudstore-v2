package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileStatus;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.model.UploadNotCompletedException;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.IdGenerator;
import com.ezcloudstore.domain.port.ShareLinkRepository;

import java.time.Clock;
import java.time.Duration;

public class CreateShareLinkUseCase {

    private final FileRepository files;
    private final ShareLinkRepository shareLinks;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateShareLinkUseCase(FileRepository files, ShareLinkRepository shareLinks,
                                  IdGenerator ids, Clock clock) {
        this.files = files;
        this.shareLinks = shareLinks;
        this.ids = ids;
        this.clock = clock;
    }

    public ShareLink handle(OwnerId owner, FileId fileId, Duration ttl) {
        StoredFile file = files.find(fileId)
                .filter(f -> f.isOwnedBy(owner))
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));
        if (file.status() != FileStatus.ACTIVE) {
            throw new UploadNotCompletedException("File " + fileId.value() + " has no shareable content yet");
        }
        ShareLink link = ShareLink.create(ids.nextShareToken(), fileId, owner, clock.instant(), ttl);
        shareLinks.save(link);
        return link;
    }
}
