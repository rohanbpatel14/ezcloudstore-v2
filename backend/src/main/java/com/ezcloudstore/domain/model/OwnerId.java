package com.ezcloudstore.domain.model;

public record OwnerId(String value) {

    public OwnerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OwnerId must not be blank");
        }
    }

    public static OwnerId of(String value) {
        return new OwnerId(value);
    }
}
