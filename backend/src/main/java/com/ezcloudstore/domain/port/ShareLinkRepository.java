package com.ezcloudstore.domain.port;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.ShareToken;

import java.util.Optional;

public interface ShareLinkRepository {

    void save(ShareLink link);

    Optional<ShareLink> find(ShareToken token);

    void delete(ShareToken token);

    void deleteAllForFile(FileId fileId);
}
