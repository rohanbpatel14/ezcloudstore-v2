package com.ezcloudstore.adapters.in.rest.dto;

import com.ezcloudstore.application.FileDetails;
import com.ezcloudstore.application.UploadTicket;
import com.ezcloudstore.domain.model.FileVersion;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.StoredFile;

import java.time.Instant;
import java.util.List;

public final class Responses {

    public record UploadTicketResponse(String fileId, String uploadUrl) {

        public static UploadTicketResponse from(UploadTicket ticket) {
            return new UploadTicketResponse(ticket.fileId().value(), ticket.uploadUrl().toString());
        }
    }

    public record FileResponse(String id, String name, String description, long sizeBytes,
                               String contentType, String status, Instant createdAt, Instant updatedAt) {

        public static FileResponse from(StoredFile file) {
            return new FileResponse(file.id().value(), file.name(), file.description(),
                    file.sizeBytes(), file.contentType(), file.status().name(),
                    file.createdAt(), file.updatedAt());
        }
    }

    public record VersionResponse(String s3VersionId, long sizeBytes, Instant createdAt) {

        public static VersionResponse from(FileVersion version) {
            return new VersionResponse(version.s3VersionId(), version.sizeBytes(), version.createdAt());
        }
    }

    public record FileDetailsResponse(String id, String name, String description, long sizeBytes,
                                      String contentType, String status, Instant createdAt,
                                      Instant updatedAt, List<VersionResponse> versions) {

        public static FileDetailsResponse from(FileDetails details) {
            StoredFile file = details.file();
            return new FileDetailsResponse(file.id().value(), file.name(), file.description(),
                    file.sizeBytes(), file.contentType(), file.status().name(),
                    file.createdAt(), file.updatedAt(),
                    details.versions().stream().map(VersionResponse::from).toList());
        }
    }

    public record ShareLinkResponse(String token, String fileId, Instant createdAt, Instant expiresAt) {

        public static ShareLinkResponse from(ShareLink link) {
            return new ShareLinkResponse(link.token().value(), link.fileId().value(),
                    link.createdAt(), link.expiresAt());
        }
    }

    private Responses() {
    }
}
