package com.ezcloudstore.domain.model;

public record FileId(String value) {

    public FileId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FileId must not be blank");
        }
    }

    public static FileId of(String value) {
        return new FileId(value);
    }
}
