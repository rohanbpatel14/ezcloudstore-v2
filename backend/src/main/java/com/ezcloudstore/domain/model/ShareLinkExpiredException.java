package com.ezcloudstore.domain.model;

public class ShareLinkExpiredException extends DomainException {

    public ShareLinkExpiredException(ShareToken token) {
        super("Share link expired: " + token.value());
    }
}
