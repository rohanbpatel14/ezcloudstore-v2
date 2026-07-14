package com.ezcloudstore.adapters.out;

import com.ezcloudstore.adapters.out.dynamodb.DynamoDbFileRepository;
import com.ezcloudstore.adapters.out.dynamodb.DynamoDbShareLinkRepository;
import com.ezcloudstore.adapters.out.dynamodb.DynamoDbTableSchema;
import com.ezcloudstore.adapters.out.s3.S3FileStorage;
import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileVersion;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.port.FileStorage.StoredObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter integration tests against LocalStack (S3 + DynamoDB).
 * Skips when Docker is unavailable locally; CI always runs it.
 */
@Testcontainers(disabledWithoutDocker = true)
class LocalStackIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE =
            DockerImageName.parse("localstack/localstack:3.8");

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.DYNAMODB);

    private static final String BUCKET = "ezcloudstore-test-files";
    private static final String TABLE = "ezcloudstore-test";
    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    private static S3Client s3;
    private static S3Presigner presigner;
    private static DynamoDbClient dynamo;

    @BeforeAll
    static void provision() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
        Region region = Region.of(LOCALSTACK.getRegion());

        s3 = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .credentialsProvider(credentials)
                .region(region)
                .forcePathStyle(true)
                .build();
        presigner = S3Presigner.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        dynamo = DynamoDbClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .credentialsProvider(credentials)
                .region(region)
                .build();

        s3.createBucket(b -> b.bucket(BUCKET));
        s3.putBucketVersioning(b -> b.bucket(BUCKET)
                .versioningConfiguration(v -> v.status(BucketVersioningStatus.ENABLED)));
        DynamoDbTableSchema.createTable(dynamo, TABLE);
    }

    private static StoredFile newFile(String id, String owner) {
        return StoredFile.create(FileId.of(id), OwnerId.of(owner), "report.pdf", "Q2 report",
                1_024, "application/pdf", NOW);
    }

    private static int httpPut(URI url, byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url.toString()).openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        try (var out = connection.getOutputStream()) {
            out.write(body);
        }
        return connection.getResponseCode();
    }

    @Nested
    class S3Storage {

        private final S3FileStorage storage = new S3FileStorage(s3, presigner, BUCKET, Duration.ofMinutes(5));

        @Test
        void presignedPutUploadsAndHeadSeesVersionedObject() throws IOException {
            StorageKey key = new StorageKey(OwnerId.of("user-s3"), FileId.of("f-up"));
            byte[] body = "hello ezcloudstore".getBytes();

            URI url = storage.presignUpload(key, body.length);
            int status = httpPut(url, body);

            assertThat(status).isEqualTo(200);
            Optional<StoredObject> head = storage.head(key);
            assertThat(head).isPresent();
            assertThat(head.get().sizeBytes()).isEqualTo(body.length);
            assertThat(head.get().s3VersionId()).isNotBlank();
        }

        @Test
        void headOnMissingKeyIsEmpty() {
            assertThat(storage.head(new StorageKey(OwnerId.of("user-s3"), FileId.of("missing"))))
                    .isEmpty();
        }

        @Test
        void presignedDownloadServesUploadedContent() throws IOException {
            StorageKey key = new StorageKey(OwnerId.of("user-s3"), FileId.of("f-down"));
            s3.putObject(b -> b.bucket(BUCKET).key(key.value()),
                    RequestBody.fromString("download me"));

            URI url = storage.presignDownload(key, null, "report.pdf");
            HttpURLConnection connection = (HttpURLConnection) new URL(url.toString()).openConnection();

            assertThat(connection.getResponseCode()).isEqualTo(200);
            assertThat(new String(connection.getInputStream().readAllBytes())).isEqualTo("download me");
        }

        @Test
        void deleteAllVersionsRemovesEveryVersion() {
            StorageKey key = new StorageKey(OwnerId.of("user-s3"), FileId.of("f-del"));
            s3.putObject(b -> b.bucket(BUCKET).key(key.value()), RequestBody.fromString("v1"));
            s3.putObject(b -> b.bucket(BUCKET).key(key.value()), RequestBody.fromString("v2"));

            storage.deleteAllVersions(key);

            assertThat(storage.head(key)).isEmpty();
            var versions = s3.listObjectVersions(b -> b.bucket(BUCKET).prefix(key.value()));
            assertThat(versions.versions()).isEmpty();
            assertThat(versions.deleteMarkers()).isEmpty();
        }
    }

    @Nested
    class DynamoFiles {

        private final DynamoDbFileRepository repository = new DynamoDbFileRepository(dynamo, TABLE);

        @Test
        void savedFileRoundTripsAllFields() {
            StoredFile file = newFile("f-rt", "user-ddb");
            file.activate("s3v-1", 2_048, NOW.plusSeconds(30));

            repository.save(file);
            StoredFile loaded = repository.find(FileId.of("f-rt")).orElseThrow();

            assertThat(loaded.id()).isEqualTo(file.id());
            assertThat(loaded.owner()).isEqualTo(file.owner());
            assertThat(loaded.name()).isEqualTo("report.pdf");
            assertThat(loaded.description()).isEqualTo("Q2 report");
            assertThat(loaded.sizeBytes()).isEqualTo(2_048);
            assertThat(loaded.contentType()).isEqualTo("application/pdf");
            assertThat(loaded.status()).isEqualTo(file.status());
            assertThat(loaded.createdAt()).isEqualTo(file.createdAt());
            assertThat(loaded.updatedAt()).isEqualTo(file.updatedAt());
            assertThat(loaded.currentS3VersionId()).isEqualTo(file.currentS3VersionId());
        }

        @Test
        void listByOwnerReturnsOnlyThatOwnersFiles() {
            repository.save(newFile("f-o1", "owner-a"));
            repository.save(newFile("f-o2", "owner-a"));
            repository.save(newFile("f-o3", "owner-b"));

            assertThat(repository.listByOwner(OwnerId.of("owner-a")))
                    .extracting(f -> f.id().value())
                    .containsExactlyInAnyOrder("f-o1", "f-o2");
        }

        @Test
        void listAllSpansOwners() {
            repository.save(newFile("f-a1", "owner-x"));
            repository.save(newFile("f-a2", "owner-y"));

            assertThat(repository.listAll())
                    .extracting(f -> f.id().value())
                    .contains("f-a1", "f-a2");
        }

        @Test
        void versionsRoundTripInOrder() {
            FileId id = FileId.of("f-ver");
            repository.save(newFile("f-ver", "owner-v"));
            repository.saveVersion(new FileVersion(id, "s3v-1", 100, NOW));
            repository.saveVersion(new FileVersion(id, "s3v-2", 200, NOW.plusSeconds(60)));

            List<FileVersion> versions = repository.listVersions(id);

            assertThat(versions).hasSize(2);
            assertThat(versions).extracting(FileVersion::s3VersionId)
                    .containsExactly("s3v-1", "s3v-2");

            repository.deleteVersions(id);
            assertThat(repository.listVersions(id)).isEmpty();
        }

        @Test
        void deleteRemovesFile() {
            repository.save(newFile("f-gone", "owner-d"));

            repository.delete(FileId.of("f-gone"));

            assertThat(repository.find(FileId.of("f-gone"))).isEmpty();
        }
    }

    @Nested
    class DynamoShareLinks {

        private final DynamoDbShareLinkRepository repository = new DynamoDbShareLinkRepository(dynamo, TABLE);

        @Test
        void shareLinkRoundTrips() {
            ShareLink link = ShareLink.create(ShareToken.of("tok-rt"), FileId.of("f-1"),
                    OwnerId.of("user-s"), NOW, Duration.ofHours(24));

            repository.save(link);
            ShareLink loaded = repository.find(ShareToken.of("tok-rt")).orElseThrow();

            assertThat(loaded.token()).isEqualTo(link.token());
            assertThat(loaded.fileId()).isEqualTo(link.fileId());
            assertThat(loaded.owner()).isEqualTo(link.owner());
            assertThat(loaded.createdAt()).isEqualTo(link.createdAt());
            assertThat(loaded.expiresAt()).isEqualTo(link.expiresAt());
        }

        @Test
        void deleteAllForFileRemovesOnlyThatFilesLinks() {
            repository.save(ShareLink.create(ShareToken.of("tok-a"), FileId.of("f-x"),
                    OwnerId.of("u"), NOW, Duration.ofHours(1)));
            repository.save(ShareLink.create(ShareToken.of("tok-b"), FileId.of("f-x"),
                    OwnerId.of("u"), NOW, Duration.ofHours(1)));
            repository.save(ShareLink.create(ShareToken.of("tok-c"), FileId.of("f-y"),
                    OwnerId.of("u"), NOW, Duration.ofHours(1)));

            repository.deleteAllForFile(FileId.of("f-x"));

            assertThat(repository.find(ShareToken.of("tok-a"))).isEmpty();
            assertThat(repository.find(ShareToken.of("tok-b"))).isEmpty();
            assertThat(repository.find(ShareToken.of("tok-c"))).isPresent();
        }

        @Test
        void deleteRemovesSingleToken() {
            repository.save(ShareLink.create(ShareToken.of("tok-solo"), FileId.of("f-z"),
                    OwnerId.of("u"), NOW, Duration.ofHours(1)));

            repository.delete(ShareToken.of("tok-solo"));

            assertThat(repository.find(ShareToken.of("tok-solo"))).isEmpty();
        }
    }
}
