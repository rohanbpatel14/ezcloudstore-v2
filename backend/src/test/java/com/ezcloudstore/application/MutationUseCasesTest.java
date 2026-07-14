package com.ezcloudstore.application;

import com.ezcloudstore.application.support.FakeFileStorage;
import com.ezcloudstore.application.support.InMemoryFileRepository;
import com.ezcloudstore.application.support.InMemoryShareLinkRepository;
import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileTooLargeException;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.model.UploadNotPendingException;
import com.ezcloudstore.domain.port.FileStorage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MutationUseCasesTest {

    private static final OwnerId OWNER = OwnerId.of("user-abc");
    private static final OwnerId STRANGER = OwnerId.of("intruder");
    private static final FileId ID = FileId.of("f-1");
    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryFileRepository files;
    private InMemoryShareLinkRepository shareLinks;
    private FakeFileStorage storage;

    @BeforeEach
    void setUp() {
        files = new InMemoryFileRepository();
        shareLinks = new InMemoryShareLinkRepository();
        storage = new FakeFileStorage();
    }

    private StoredFile activeFile() {
        StoredFile file = StoredFile.create(ID, OWNER, "report.pdf", "d", 100, "application/pdf", NOW.minusSeconds(60));
        files.save(file);
        storage.putObject(StorageKey.from(file), new StoredObject("s3v-1", 100));
        return new CompleteUploadUseCase(files, storage, CLOCK).handle(OWNER, ID);
    }

    @Nested
    class UpdateDescription {

        @Test
        void updatesAndPersists() {
            activeFile();

            UpdateDescriptionUseCase useCase = new UpdateDescriptionUseCase(files, CLOCK);
            StoredFile result = useCase.handle(OWNER, ID, "Final version");

            assertThat(result.description()).isEqualTo("Final version");
            assertThat(files.find(ID).orElseThrow().description()).isEqualTo("Final version");
        }

        @Test
        void foreignFileReadsAsNotFound() {
            activeFile();

            UpdateDescriptionUseCase useCase = new UpdateDescriptionUseCase(files, CLOCK);

            assertThatExceptionOfType(StoredFileNotFoundException.class)
                    .isThrownBy(() -> useCase.handle(STRANGER, ID, "x"));
        }
    }

    @Nested
    class InitiateVersionUpload {

        @Test
        void presignsSameStorageKeyForActiveFile() {
            activeFile();

            InitiateVersionUploadUseCase useCase = new InitiateVersionUploadUseCase(files, storage);
            UploadTicket ticket = useCase.handle(OWNER, ID, 2_048);

            assertThat(ticket.fileId()).isEqualTo(ID);
            assertThat(storage.uploadCalls).hasSize(1);
            assertThat(storage.uploadCalls.getFirst().key().value()).isEqualTo("user-abc/f-1");
            assertThat(storage.uploadCalls.getFirst().contentLengthBytes()).isEqualTo(2_048);
        }

        @Test
        void pendingFileCannotTakeNewVersions() {
            StoredFile pending = StoredFile.create(ID, OWNER, "report.pdf", "d", 100, "application/pdf", NOW);
            files.save(pending);

            InitiateVersionUploadUseCase useCase = new InitiateVersionUploadUseCase(files, storage);

            assertThatExceptionOfType(UploadNotPendingException.class)
                    .isThrownBy(() -> useCase.handle(OWNER, ID, 2_048));
        }

        @Test
        void oversizedVersionIsRejected() {
            activeFile();

            InitiateVersionUploadUseCase useCase = new InitiateVersionUploadUseCase(files, storage);

            assertThatExceptionOfType(FileTooLargeException.class)
                    .isThrownBy(() -> useCase.handle(OWNER, ID, StoredFile.MAX_SIZE_BYTES + 1));
        }
    }

    @Nested
    class DeleteFile {

        @Test
        void removesMetadataVersionsShareLinksAndStorage() {
            StoredFile file = activeFile();
            shareLinks.save(ShareLink.create(ShareToken.of("tok-1"), ID, OWNER, NOW, Duration.ofHours(1)));

            DeleteFileUseCase useCase = new DeleteFileUseCase(files, shareLinks, storage);
            useCase.handle(OWNER, ID);

            assertThat(files.find(ID)).isEmpty();
            assertThat(files.listVersions(ID)).isEmpty();
            assertThat(shareLinks.find(ShareToken.of("tok-1"))).isEmpty();
            assertThat(storage.deletedKeys).containsExactly(StorageKey.from(file));
        }

        @Test
        void foreignFileReadsAsNotFound() {
            activeFile();

            DeleteFileUseCase useCase = new DeleteFileUseCase(files, shareLinks, storage);

            assertThatExceptionOfType(StoredFileNotFoundException.class)
                    .isThrownBy(() -> useCase.handle(STRANGER, ID));
        }
    }
}
