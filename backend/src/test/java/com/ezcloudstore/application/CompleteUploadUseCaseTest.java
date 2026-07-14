package com.ezcloudstore.application;

import com.ezcloudstore.application.support.FakeFileStorage;
import com.ezcloudstore.application.support.InMemoryFileRepository;
import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileStatus;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.model.UploadNotCompletedException;
import com.ezcloudstore.domain.port.FileStorage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CompleteUploadUseCaseTest {

    private static final OwnerId OWNER = OwnerId.of("user-abc");
    private static final FileId ID = FileId.of("f-1");
    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    private InMemoryFileRepository files;
    private FakeFileStorage storage;
    private CompleteUploadUseCase useCase;

    @BeforeEach
    void setUp() {
        files = new InMemoryFileRepository();
        storage = new FakeFileStorage();
        useCase = new CompleteUploadUseCase(files, storage, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private StoredFile pendingFile() {
        StoredFile file = StoredFile.create(ID, OWNER, "report.pdf", "d", 1_024, "application/pdf", NOW.minusSeconds(60));
        files.save(file);
        return file;
    }

    @Test
    void activatesPendingFileFromS3ObjectAndRecordsFirstVersion() {
        StoredFile file = pendingFile();
        storage.putObject(StorageKey.from(file), new StoredObject("s3v-1", 2_048));

        StoredFile result = useCase.handle(OWNER, ID);

        assertThat(result.status()).isEqualTo(FileStatus.ACTIVE);
        assertThat(result.currentS3VersionId()).contains("s3v-1");
        assertThat(result.sizeBytes()).isEqualTo(2_048);
        assertThat(files.listVersions(ID)).hasSize(1);
        assertThat(files.listVersions(ID).getFirst().s3VersionId()).isEqualTo("s3v-1");
    }

    @Test
    void recordsNewVersionWhenActiveFileHasFreshS3Object() {
        StoredFile file = pendingFile();
        storage.putObject(StorageKey.from(file), new StoredObject("s3v-1", 2_048));
        useCase.handle(OWNER, ID);

        storage.putObject(StorageKey.from(file), new StoredObject("s3v-2", 4_096));
        StoredFile result = useCase.handle(OWNER, ID);

        assertThat(result.currentS3VersionId()).contains("s3v-2");
        assertThat(result.sizeBytes()).isEqualTo(4_096);
        assertThat(files.listVersions(ID)).hasSize(2);
    }

    @Test
    void rejectsCompletionWhenNoObjectWasUploaded() {
        pendingFile();

        assertThatExceptionOfType(UploadNotCompletedException.class)
                .isThrownBy(() -> useCase.handle(OWNER, ID));
    }

    @Test
    void rejectsCompletionWhenActiveFileHasNoNewS3Version() {
        StoredFile file = pendingFile();
        storage.putObject(StorageKey.from(file), new StoredObject("s3v-1", 2_048));
        useCase.handle(OWNER, ID);

        assertThatExceptionOfType(UploadNotCompletedException.class)
                .isThrownBy(() -> useCase.handle(OWNER, ID));

        assertThat(files.listVersions(ID)).hasSize(1);
    }

    @Test
    void unknownFileReadsAsNotFound() {
        assertThatExceptionOfType(StoredFileNotFoundException.class)
                .isThrownBy(() -> useCase.handle(OWNER, FileId.of("nope")));
    }

    @Test
    void foreignFileReadsAsNotFoundToPreventEnumeration() {
        StoredFile file = pendingFile();
        storage.putObject(StorageKey.from(file), new StoredObject("s3v-1", 2_048));

        assertThatExceptionOfType(StoredFileNotFoundException.class)
                .isThrownBy(() -> useCase.handle(OwnerId.of("intruder"), ID));
    }
}
