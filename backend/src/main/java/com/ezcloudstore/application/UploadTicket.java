package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileId;

import java.net.URI;

public record UploadTicket(FileId fileId, URI uploadUrl) {
}
