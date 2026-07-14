package com.ezcloudstore.application.support;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.port.ShareLinkRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryShareLinkRepository implements ShareLinkRepository {

    private final Map<ShareToken, ShareLink> links = new LinkedHashMap<>();

    public void clear() {
        links.clear();
    }

    @Override
    public void save(ShareLink link) {
        links.put(link.token(), link);
    }

    @Override
    public Optional<ShareLink> find(ShareToken token) {
        return Optional.ofNullable(links.get(token));
    }

    @Override
    public void delete(ShareToken token) {
        links.remove(token);
    }

    @Override
    public void deleteAllForFile(FileId fileId) {
        links.values().removeIf(link -> link.fileId().equals(fileId));
    }
}
