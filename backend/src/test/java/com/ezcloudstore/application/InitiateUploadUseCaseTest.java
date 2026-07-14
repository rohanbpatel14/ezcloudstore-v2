package com.ezcloudstore.application;

import com.ezcloudstore.application.support.FakeFileStorage;
import com.ezcloudstore.application.support.FixedIdGenerator;
import com.ezcloudstore.application.support.InMemoryFileRepository;
import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileStatus;
import com.ezcloudstore.domain.model.FileTooLargeException;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class InitiateUploadUseCaseTest {

    private static final OwnerId OWNER = OwnerId.of("user-abc");
    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    private InMemoryFileRepository files;
    private FakeFileStorage storage;
    private InitiateUploadUseCase useCase;

    @BeforeEach
    void setUp() {
        files = new InMemoryFileRepository();
        storage = new FakeFileStorage();
        FixedIdGenerator ids = new FixedIdGenerator().willReturnFileIds("f-1");
        useCase = new InitiateUploadUseCase(files, storage, ids, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reservesPendingFileAndReturnsPresignedUploadUrl() {
        UploadTicket ticket = useCase.handle(OWNER, "report.pdf", "Q2 report", 1_024, "application/pdf");

        assertThat(ticket.fileId()).isEqualTo(FileId.of("f-1"));
        assertThat(ticket.uploadUrl()).isEqualTo(URI.create("https://s3.fake/upload/user-abc/f-1"));

        StoredFile saved = files.find(FileId.of("f-1")).orElseThrow();
        assertThat(saved.status()).isEqualTo(FileStatus.PENDING_UPLOAD);
        assertThat(saved.name()).isEqualTo("report.pdf");
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void presignPinsTheDeclaredContentLength() {
        useCase.handle(OWNER, "report.pdf", "Q2 report", 1_024, "application/pdf");

        assertThat(storage.uploadCalls).hasSize(1);
        assertThat(storage.uploadCalls.getFirst().contentLengthBytes()).isEqualTo(1_024);
    }

    @Test
    void oversizedDeclaredUploadIsRejectedAndNothingPersisted() {
        assertThatExceptionOfType(FileTooLargeException.class)
                .isThrownBy(() -> useCase.handle(OWNER, "big.bin", "d", StoredFile.MAX_SIZE_BYTES + 1, "application/octet-stream"));

        assertThat(files.listByOwner(OWNER)).isEmpty();
        assertThat(storage.uploadCalls).isEmpty();
    }
}
