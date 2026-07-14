package com.ezcloudstore.adapters.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public final class Requests {

    public record InitiateUpload(
            @NotBlank String name,
            String description,
            @Positive long sizeBytes,
            @NotBlank String contentType) {
    }

    public record NewVersion(@Positive long sizeBytes) {
    }

    public record UpdateFile(String description) {
    }

    public record CreateShare(@Positive @Max(168) int ttlHours) {
    }

    private Requests() {
    }
}
