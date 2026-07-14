package com.ezcloudstore.application;

import com.ezcloudstore.application.support.FakeFileStorage;
import com.ezcloudstore.application.support.FixedIdGenerator;
import com.ezcloudstore.application.support.InMemoryFileRepository;
import com.ezcloudstore.application.support.InMemoryShareLinkRepository;
import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.ShareLinkExpiredException;
import com.ezcloudstore.domain.model.ShareLinkNotFoundException;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.port.FileStorage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ShareLinkUseCasesTest {

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
    class Create {

        @Test
        void persistsLinkWithGeneratedToken() {
            activeFile();
            FixedIdGenerator ids = new FixedIdGenerator().willReturnTokens("tok-1");

            CreateShareLinkUseCase useCase = new CreateShareLinkUseCase(files, shareLinks, ids, CLOCK);
            ShareLink link = useCase.handle(OWNER, ID, Duration.ofHours(24));

            assertThat(link.token()).isEqualTo(ShareToken.of("tok-1"));
            assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
            assertThat(shareLinks.find(ShareToken.of("tok-1"))).isPresent();
        }

        @Test
        void foreignFileReadsAsNotFound() {
            activeFile();
            FixedIdGenerator ids = new FixedIdGenerator().willReturnTokens("tok-1");

            CreateShareLinkUseCase useCase = new CreateShareLinkUseCase(files, shareLinks, ids, CLOCK);

            assertThatExceptionOfType(StoredFileNotFoundException.class)
                    .isThrownBy(() -> useCase.handle(STRANGER, ID, Duration.ofHours(1)));
        }
    }

    @Nested
    class Revoke {

        @Test
        void ownerCanRevoke() {
            activeFile();
            shareLinks.save(ShareLink.create(ShareToken.of("tok-1"), ID, OWNER, NOW, Duration.ofHours(1)));

            RevokeShareLinkUseCase useCase = new RevokeShareLinkUseCase(shareLinks);
            useCase.handle(OWNER, ShareToken.of("tok-1"));

            assertThat(shareLinks.find(ShareToken.of("tok-1"))).isEmpty();
        }

        @Test
        void strangerCannotRevokeAndSeesNotFound() {
            activeFile();
            shareLinks.save(ShareLink.create(ShareToken.of("tok-1"), ID, OWNER, NOW, Duration.ofHours(1)));

            RevokeShareLinkUseCase useCase = new RevokeShareLinkUseCase(shareLinks);

            assertThatExceptionOfType(ShareLinkNotFoundException.class)
                    .isThrownBy(() -> useCase.handle(STRANGER, ShareToken.of("tok-1")));
            assertThat(shareLinks.find(ShareToken.of("tok-1"))).isPresent();
        }
    }

    @Nested
    class Resolve {

        @Test
        void resolvesToPresignedDownloadOfCurrentVersion() {
            activeFile();
            shareLinks.save(ShareLink.create(ShareToken.of("tok-1"), ID, OWNER, NOW, Duration.ofHours(1)));

            ResolveShareLinkUseCase useCase = new ResolveShareLinkUseCase(shareLinks, files, storage, CLOCK);
            URI url = useCase.handle(ShareToken.of("tok-1"));

            assertThat(url.toString()).contains("user-abc/f-1");
            assertThat(storage.downloadCalls.getFirst().downloadFileName()).isEqualTo("report.pdf");
        }

        @Test
        void expiredLinkIsRejected() {
            activeFile();
            shareLinks.save(ShareLink.create(ShareToken.of("tok-1"), ID, OWNER,
                    NOW.minus(Duration.ofHours(2)), Duration.ofHours(1)));

            ResolveShareLinkUseCase useCase = new ResolveShareLinkUseCase(shareLinks, files, storage, CLOCK);

            assertThatExceptionOfType(ShareLinkExpiredException.class)
                    .isThrownBy(() -> useCase.handle(ShareToken.of("tok-1")));
        }

        @Test
        void unknownTokenReadsAsNotFound() {
            ResolveShareLinkUseCase useCase = new ResolveShareLinkUseCase(shareLinks, files, storage, CLOCK);

            assertThatExceptionOfType(ShareLinkNotFoundException.class)
                    .isThrownBy(() -> useCase.handle(ShareToken.of("nope")));
        }

        @Test
        void linkToDeletedFileReadsAsNotFound() {
            activeFile();
            shareLinks.save(ShareLink.create(ShareToken.of("tok-1"), ID, OWNER, NOW, Duration.ofHours(1)));
            files.delete(ID);

            ResolveShareLinkUseCase useCase = new ResolveShareLinkUseCase(shareLinks, files, storage, CLOCK);

            assertThatExceptionOfType(ShareLinkNotFoundException.class)
                    .isThrownBy(() -> useCase.handle(ShareToken.of("tok-1")));
        }
    }

    @Nested
    class Admin {

        @Test
        void adminListSeesAllOwnersFiles() {
            activeFile();
            StoredFile foreign = StoredFile.create(FileId.of("f-9"), STRANGER, "x.txt", "d", 100, "text/plain", NOW);
            files.save(foreign);

            AdminListFilesUseCase useCase = new AdminListFilesUseCase(files);

            assertThat(useCase.handle())
                    .extracting(f -> f.id().value())
                    .containsExactlyInAnyOrder("f-1", "f-9");
        }

        @Test
        void adminDeleteRemovesAnyFileWithoutOwnershipCheck() {
            StoredFile file = activeFile();
            shareLinks.save(ShareLink.create(ShareToken.of("tok-1"), ID, OWNER, NOW, Duration.ofHours(1)));

            AdminDeleteFileUseCase useCase = new AdminDeleteFileUseCase(files, shareLinks, storage);
            useCase.handle(ID);

            assertThat(files.find(ID)).isEmpty();
            assertThat(files.listVersions(ID)).isEmpty();
            assertThat(shareLinks.find(ShareToken.of("tok-1"))).isEmpty();
            assertThat(storage.deletedKeys).containsExactly(StorageKey.from(file));
        }
    }
}
