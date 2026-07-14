package com.ezcloudstore.application;

import com.ezcloudstore.domain.model.FileVersion;
import com.ezcloudstore.domain.model.StoredFile;

import java.util.List;

public record FileDetails(StoredFile file, List<FileVersion> versions) {
}
