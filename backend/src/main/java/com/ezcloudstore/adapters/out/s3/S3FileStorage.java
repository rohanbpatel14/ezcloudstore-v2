package com.ezcloudstore.adapters.out.s3;

import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.port.FileStorage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FileStorage over S3 presigned URLs (ADR-0003). The presigned PUT signs the
 * exact Content-Length, so S3 itself rejects bodies that differ from the
 * declared size — the 10MB cap needs no server-side byte counting.
 */
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration presignTtl;

    public S3FileStorage(S3Client s3, S3Presigner presigner, String bucket, Duration presignTtl) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
        this.presignTtl = presignTtl;
    }

    @Override
    public URI presignUpload(StorageKey key, long contentLengthBytes) {
        var presigned = presigner.presignPutObject(p -> p
                .signatureDuration(presignTtl)
                .putObjectRequest(put -> put
                        .bucket(bucket)
                        .key(key.value())
                        .contentLength(contentLengthBytes)));
        return toUri(presigned.url());
    }

    @Override
    public URI presignDownload(StorageKey key, String s3VersionId, String downloadFileName) {
        var presigned = presigner.presignGetObject(p -> p
                .signatureDuration(presignTtl)
                .getObjectRequest(get -> {
                    get.bucket(bucket)
                            .key(key.value())
                            .responseContentDisposition(
                                    "attachment; filename=\"" + sanitize(downloadFileName) + "\"");
                    if (s3VersionId != null) {
                        get.versionId(s3VersionId);
                    }
                }));
        return toUri(presigned.url());
    }

    @Override
    public Optional<StoredObject> head(StorageKey key) {
        try {
            HeadObjectResponse head = s3.headObject(h -> h.bucket(bucket).key(key.value()));
            return Optional.of(new StoredObject(head.versionId(), head.contentLength()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteAllVersions(StorageKey key) {
        String exactKey = key.value();
        String keyMarker = null;
        String versionMarker = null;
        do {
            String km = keyMarker;
            String vm = versionMarker;
            ListObjectVersionsResponse page = s3.listObjectVersions(l -> {
                l.bucket(bucket).prefix(exactKey);
                if (km != null) {
                    l.keyMarker(km).versionIdMarker(vm);
                }
            });

            List<ObjectIdentifier> toDelete = new ArrayList<>();
            page.versions().stream()
                    .filter(v -> v.key().equals(exactKey))
                    .forEach(v -> toDelete.add(ObjectIdentifier.builder()
                            .key(v.key()).versionId(v.versionId()).build()));
            page.deleteMarkers().stream()
                    .filter(m -> m.key().equals(exactKey))
                    .forEach(m -> toDelete.add(ObjectIdentifier.builder()
                            .key(m.key()).versionId(m.versionId()).build()));

            if (!toDelete.isEmpty()) {
                s3.deleteObjects(d -> d.bucket(bucket)
                        .delete(Delete.builder().objects(toDelete).build()));
            }

            keyMarker = page.isTruncated() ? page.nextKeyMarker() : null;
            versionMarker = page.isTruncated() ? page.nextVersionIdMarker() : null;
        } while (keyMarker != null);
    }

    private static URI toUri(java.net.URL url) {
        try {
            return url.toURI();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Presigner produced an invalid URL", e);
        }
    }

    private static String sanitize(String fileName) {
        return fileName == null ? "download" : fileName.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
