package com.ezcloudstore.domain.model;

public record ShareToken(String value) {

    public ShareToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ShareToken must not be blank");
        }
    }

    public static ShareToken of(String value) {
        return new ShareToken(value);
    }
}
