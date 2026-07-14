package com.ezcloudstore.application.support;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.port.IdGenerator;

import java.util.ArrayDeque;
import java.util.Deque;

public class FixedIdGenerator implements IdGenerator {

    private final Deque<String> fileIds = new ArrayDeque<>();
    private final Deque<String> tokens = new ArrayDeque<>();

    public void clear() {
        fileIds.clear();
        tokens.clear();
    }

    public FixedIdGenerator willReturnFileIds(String... ids) {
        for (String id : ids) {
            fileIds.add(id);
        }
        return this;
    }

    public FixedIdGenerator willReturnTokens(String... values) {
        for (String value : values) {
            tokens.add(value);
        }
        return this;
    }

    @Override
    public FileId nextFileId() {
        return FileId.of(fileIds.removeFirst());
    }

    @Override
    public ShareToken nextShareToken() {
        return ShareToken.of(tokens.removeFirst());
    }
}
