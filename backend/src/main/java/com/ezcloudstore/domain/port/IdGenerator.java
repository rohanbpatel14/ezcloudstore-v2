package com.ezcloudstore.domain.port;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.ShareToken;

public interface IdGenerator {

    FileId nextFileId();

    ShareToken nextShareToken();
}
