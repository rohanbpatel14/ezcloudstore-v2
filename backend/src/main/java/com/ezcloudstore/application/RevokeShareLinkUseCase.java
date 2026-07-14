package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.ShareLinkNotFoundException;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.port.ShareLinkRepository;

public class RevokeShareLinkUseCase {

    private final ShareLinkRepository shareLinks;

    public RevokeShareLinkUseCase(ShareLinkRepository shareLinks) {
        this.shareLinks = shareLinks;
    }

    public void handle(OwnerId owner, ShareToken token) {
        ShareLink link = shareLinks.find(token)
                .filter(l -> l.owner().equals(owner))
                .orElseThrow(() -> new ShareLinkNotFoundException(token));
        shareLinks.delete(link.token());
    }
}
