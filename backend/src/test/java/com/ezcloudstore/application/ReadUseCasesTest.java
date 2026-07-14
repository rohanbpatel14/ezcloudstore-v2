package com.ezcloudstore.application;

import com.ezcloudstore.application.support.FakeFileStorage;
import com.ezcloudstore.application.support.InMemoryFileRepository;
import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.model.UploadNotCompletedException;
import com.ezcloudstore.domain.port.FileStorage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ReadUseCasesTest {

    private static final OwnerId OWNER = OwnerId.of("user-abc");
    private static final OwnerId STRANGER = OwnerId.of("intruder");
    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    private InMemoryFileRepository files;
    private FakeFileStorage storage;
    private CompleteUploadUseCase completeUpload;

    @BeforeEach
    void setUp() {
        files = new InMemoryFileRepository();
        storage = new FakeFileStorage();
        completeUpload = new CompleteUploadUseCase(files, storage, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private StoredFile activeFile(String id, String name) {
        StoredFile file = StoredFile.create(FileId.of(id), OWNER, name, "d", 100, "text/plain", NOW.minusSeconds(60));
        files.save(file);
        storage.putObject(StorageKey.from(file), new StoredObject("s3v-" + id, 100));
        return completeUpload.handle(OWNER, FileId.of(id));
    }

    @Nested
    class ListFiles {

        @Test
        void returnsOnlyOwnFiles() {
            activeFile("f-1", "a.txt");
            activeFile("f-2", "b.txt");
            StoredFile foreign = StoredFile.create(FileId.of("f-9"), STRANGER, "x.txt", "d", 100, "text/plain", NOW);
            files.save(foreign);

            ListFilesUseCase useCase = new ListFilesUseCase(files);

            assertThat(useCase.handle(OWNER))
                    .extracting(f -> f.id().value())
                    .containsExactly("f-1", "f-2");
        }
    }

    @Nested
    class GetFile {

        @Test
        void returnsFileWithItsVersions() {
            activeFile("f-1", "a.txt");

            GetFileUseCase useCase = new GetFileUseCase(files);
            FileDetails details = useCase.handle(OWNER, FileId.of("f-1"));

            assertThat(details.file().id()).isEqualTo(FileId.of("f-1"));
            assertThat(details.versions()).hasSize(1);
            assertThat(details.versions().getFirst().s3VersionId()).isEqualTo("s3v-f-1");
        }

        @Test
        void foreignFileReadsAsNotFound() {
            activeFile("f-1", "a.txt");

            GetFileUseCase useCase = new GetFileUseCase(files);

            assertThatExceptionOfType(StoredFileNotFoundException.class)
                    .isThrownBy(() -> useCase.handle(STRANGER, FileId.of("f-1")));
        }
    }

    @Nested
    class GetDownloadUrl {

        @Test
        void presignsCurrentVersionWithOriginalFileName() {
            activeFile("f-1", "a.txt");

            GetDownloadUrlUseCase useCase = new GetDownloadUrlUseCase(files, storage);
            URI url = useCase.handle(OWNER, FileId.of("f-1"), null);

            assertThat(url.toString()).contains("user-abc/f-1");
            assertThat(storage.downloadCalls).hasSize(1);
            assertThat(storage.downloadCalls.getFirst().s3VersionId()).isNull();
            assertThat(storage.downloadCalls.getFirst().downloadFileName()).isEqualTo("a.txt");
        }

        @Test
        void presignsSpecificVersionWhenRequested() {
            activeFile("f-1", "a.txt");

            GetDownloadUrlUseCase useCase = new GetDownloadUrlUseCase(files, storage);
            useCase.handle(OWNER, FileId.of("f-1"), "s3v-f-1");

            assertThat(storage.downloadCalls.getFirst().s3VersionId()).isEqualTo("s3v-f-1");
        }

        @Test
        void pendingFileHasNothingToDownload() {
            StoredFile pending = StoredFile.create(FileId.of("f-3"), OWNER, "c.txt", "d", 100, "text/plain", NOW);
            files.save(pending);

            GetDownloadUrlUseCase useCase = new GetDownloadUrlUseCase(files, storage);

            assertThatExceptionOfType(UploadNotCompletedException.class)
                    .isThrownBy(() -> useCase.handle(OWNER, FileId.of("f-3"), null));
        }
    }
}
