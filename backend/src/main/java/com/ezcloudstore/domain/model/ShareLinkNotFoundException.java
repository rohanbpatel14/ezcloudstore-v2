package com.ezcloudstore.domain.model;

/**
 * Same signal for "doesn't exist" and "not yours" — prevents token probing.
 */
public class ShareLinkNotFoundException extends DomainException {

    public ShareLinkNotFoundException(ShareToken token) {
        super("Share link not found: " + token.value());
    }
}
