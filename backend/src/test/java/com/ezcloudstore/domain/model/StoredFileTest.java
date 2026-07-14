package com.ezcloudstore.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class StoredFileTest {

    private static final FileId ID = FileId.of("f-123");
    private static final OwnerId OWNER = OwnerId.of("user-abc");
    private static final Instant T0 = Instant.parse("2026-07-13T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-13T13:00:00Z");

    private StoredFile pendingFile() {
        return StoredFile.create(ID, OWNER, "report.pdf", "Q2 report", 1_024, "application/pdf", T0);
    }

    @Test
    void createStartsPendingUpload() {
        StoredFile file = pendingFile();

        assertThat(file.id()).isEqualTo(ID);
        assertThat(file.owner()).isEqualTo(OWNER);
        assertThat(file.name()).isEqualTo("report.pdf");
        assertThat(file.description()).isEqualTo("Q2 report");
        assertThat(file.sizeBytes()).isEqualTo(1_024);
        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.status()).isEqualTo(FileStatus.PENDING_UPLOAD);
        assertThat(file.createdAt()).isEqualTo(T0);
        assertThat(file.updatedAt()).isEqualTo(T0);
        assertThat(file.currentS3VersionId()).isEmpty();
    }

    @Test
    void createRejectsBlankName() {
        assertThatExceptionOfType(InvalidFileNameException.class)
                .isThrownBy(() -> StoredFile.create(ID, OWNER, "   ", "d", 10, "text/plain", T0));
    }

    @Test
    void createRejectsFilesOverTenMegabytes() {
        long overLimit = StoredFile.MAX_SIZE_BYTES + 1;

        assertThatExceptionOfType(FileTooLargeException.class)
                .isThrownBy(() -> StoredFile.create(ID, OWNER, "big.bin", "d", overLimit, "application/octet-stream", T0));
    }

    @Test
    void createRejectsNonPositiveSize() {
        assertThatExceptionOfType(FileTooLargeException.class)
                .isThrownBy(() -> StoredFile.create(ID, OWNER, "empty.bin", "d", 0, "application/octet-stream", T0));
    }

    @Test
    void activateTransitionsPendingToActiveAndRecordsS3Version() {
        StoredFile file = pendingFile();

        file.activate("s3v-1", 2_048, T1);

        assertThat(file.status()).isEqualTo(FileStatus.ACTIVE);
        assertThat(file.currentS3VersionId()).contains("s3v-1");
        assertThat(file.sizeBytes()).isEqualTo(2_048);
        assertThat(file.updatedAt()).isEqualTo(T1);
    }

    @Test
    void activateRejectedWhenAlreadyActive() {
        StoredFile file = pendingFile();
        file.activate("s3v-1", 2_048, T1);

        assertThatExceptionOfType(UploadNotPendingException.class)
                .isThrownBy(() -> file.activate("s3v-2", 100, T1));
    }

    @Test
    void recordNewVersionReplacesCurrentVersionOnActiveFile() {
        StoredFile file = pendingFile();
        file.activate("s3v-1", 2_048, T1);
        Instant t2 = T1.plusSeconds(60);

        file.recordNewVersion("s3v-2", 4_096, t2);

        assertThat(file.currentS3VersionId()).contains("s3v-2");
        assertThat(file.sizeBytes()).isEqualTo(4_096);
        assertThat(file.updatedAt()).isEqualTo(t2);
        assertThat(file.status()).isEqualTo(FileStatus.ACTIVE);
    }

    @Test
    void recordNewVersionRejectedWhileUploadPending() {
        StoredFile file = pendingFile();

        assertThatExceptionOfType(UploadNotPendingException.class)
                .isThrownBy(() -> file.recordNewVersion("s3v-9", 10, T1));
    }

    @Test
    void updateDescriptionTouchesUpdatedAt() {
        StoredFile file = pendingFile();

        file.updateDescription("Final Q2 report", T1);

        assertThat(file.description()).isEqualTo("Final Q2 report");
        assertThat(file.updatedAt()).isEqualTo(T1);
    }

    @Test
    void isOwnedByComparesOwner() {
        StoredFile file = pendingFile();

        assertThat(file.isOwnedBy(OwnerId.of("user-abc"))).isTrue();
        assertThat(file.isOwnedBy(OwnerId.of("someone-else"))).isFalse();
    }
}
