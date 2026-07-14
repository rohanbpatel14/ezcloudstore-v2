package com.ezcloudstore.adapters.in.rest;

import com.ezcloudstore.application.AdminDeleteFileUseCase;
import com.ezcloudstore.application.AdminListFilesUseCase;
import com.ezcloudstore.application.CompleteUploadUseCase;
import com.ezcloudstore.application.CreateShareLinkUseCase;
import com.ezcloudstore.application.DeleteFileUseCase;
import com.ezcloudstore.application.GetDownloadUrlUseCase;
import com.ezcloudstore.application.GetFileUseCase;
import com.ezcloudstore.application.InitiateUploadUseCase;
import com.ezcloudstore.application.InitiateVersionUploadUseCase;
import com.ezcloudstore.application.ListFilesUseCase;
import com.ezcloudstore.application.ResolveShareLinkUseCase;
import com.ezcloudstore.application.RevokeShareLinkUseCase;
import com.ezcloudstore.application.UpdateDescriptionUseCase;
import com.ezcloudstore.application.support.FakeFileStorage;
import com.ezcloudstore.application.support.FixedIdGenerator;
import com.ezcloudstore.application.support.InMemoryFileRepository;
import com.ezcloudstore.application.support.InMemoryShareLinkRepository;
import com.ezcloudstore.config.SecurityConfig;
import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StorageKey;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.port.FileStorage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {FilesController.class, SharesController.class,
        PublicShareController.class, AdminController.class})
@Import({SecurityConfig.class, RestApiTest.Wiring.class})
class RestApiTest {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    @TestConfiguration
    static class Wiring {

        static final InMemoryFileRepository files = new InMemoryFileRepository();
        static final InMemoryShareLinkRepository shareLinks = new InMemoryShareLinkRepository();
        static final FakeFileStorage storage = new FakeFileStorage();
        static final FixedIdGenerator ids = new FixedIdGenerator();
        static final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("decoder unused: tests inject authentication");
            };
        }

        @Bean
        InitiateUploadUseCase initiateUpload() {
            return new InitiateUploadUseCase(files, storage, ids, clock);
        }

        @Bean
        CompleteUploadUseCase completeUpload() {
            return new CompleteUploadUseCase(files, storage, clock);
        }

        @Bean
        ListFilesUseCase listFiles() {
            return new ListFilesUseCase(files);
        }

        @Bean
        GetFileUseCase getFile() {
            return new GetFileUseCase(files);
        }

        @Bean
        GetDownloadUrlUseCase getDownloadUrl() {
            return new GetDownloadUrlUseCase(files, storage);
        }

        @Bean
        UpdateDescriptionUseCase updateDescription() {
            return new UpdateDescriptionUseCase(files, clock);
        }

        @Bean
        InitiateVersionUploadUseCase initiateVersionUpload() {
            return new InitiateVersionUploadUseCase(files, storage);
        }

        @Bean
        DeleteFileUseCase deleteFile() {
            return new DeleteFileUseCase(files, shareLinks, storage);
        }

        @Bean
        CreateShareLinkUseCase createShareLink() {
            return new CreateShareLinkUseCase(files, shareLinks, ids, clock);
        }

        @Bean
        RevokeShareLinkUseCase revokeShareLink() {
            return new RevokeShareLinkUseCase(shareLinks);
        }

        @Bean
        ResolveShareLinkUseCase resolveShareLink() {
            return new ResolveShareLinkUseCase(shareLinks, files, storage, clock);
        }

        @Bean
        AdminListFilesUseCase adminListFiles() {
            return new AdminListFilesUseCase(files);
        }

        @Bean
        AdminDeleteFileUseCase adminDeleteFile() {
            return new AdminDeleteFileUseCase(files, shareLinks, storage);
        }
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void resetState() {
        Wiring.files.clear();
        Wiring.shareLinks.clear();
        Wiring.storage.clear();
        Wiring.ids.clear();
    }

    private RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("user-abc"));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin-1"))
                .authorities(() -> "ROLE_admin");
    }

    private StoredFile activeFile(String id, String owner) {
        StoredFile file = StoredFile.create(FileId.of(id), OwnerId.of(owner), "report.pdf", "d",
                100, "application/pdf", NOW.minusSeconds(60));
        Wiring.files.save(file);
        Wiring.storage.putObject(StorageKey.from(file), new StoredObject("s3v-" + id, 100));
        return new CompleteUploadUseCase(Wiring.files, Wiring.storage, Wiring.clock)
                .handle(OwnerId.of(owner), FileId.of(id));
    }

    @Nested
    class Authentication {

        @Test
        void anonymousRequestsAreRejected() throws Exception {
            mvc.perform(get("/files"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void healthIsPublic() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isNotFound()); // actuator not mapped in slice; must NOT be 401
        }
    }

    @Nested
    class Files {

        @Test
        void initiateUploadReturnsTicket() throws Exception {
            Wiring.ids.willReturnFileIds("f-1");

            mvc.perform(post("/files").with(user())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"report.pdf","description":"Q2","sizeBytes":1024,"contentType":"application/pdf"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fileId").value("f-1"))
                    .andExpect(jsonPath("$.uploadUrl").value("https://s3.fake/upload/user-abc/f-1"));
        }

        @Test
        void initiateUploadValidatesBody() throws Exception {
            mvc.perform(post("/files").with(user())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"","sizeBytes":0,"contentType":""}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void oversizedUploadMapsTo413() throws Exception {
            Wiring.ids.willReturnFileIds("f-1");

            mvc.perform(post("/files").with(user())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"big.bin","sizeBytes":99999999,"contentType":"application/octet-stream"}
                                    """))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.title").value("File Too Large"));
        }

        @Test
        void completeActivatesAndReturnsFile() throws Exception {
            Wiring.ids.willReturnFileIds("f-1");
            mvc.perform(post("/files").with(user())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"report.pdf","sizeBytes":1024,"contentType":"application/pdf"}
                            """));
            Wiring.storage.putObject(
                    new StorageKey(OwnerId.of("user-abc"), FileId.of("f-1")),
                    new StoredObject("s3v-1", 1024));

            mvc.perform(post("/files/f-1/complete").with(user()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("f-1"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void completeWithoutUploadMapsTo409() throws Exception {
            Wiring.ids.willReturnFileIds("f-1");
            mvc.perform(post("/files").with(user())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"report.pdf","sizeBytes":1024,"contentType":"application/pdf"}
                            """));

            mvc.perform(post("/files/f-1/complete").with(user()))
                    .andExpect(status().isConflict());
        }

        @Test
        void listReturnsOwnFilesOnly() throws Exception {
            activeFile("f-1", "user-abc");
            activeFile("f-9", "someone-else");

            mvc.perform(get("/files").with(user()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value("f-1"));
        }

        @Test
        void getReturnsDetailsWithVersions() throws Exception {
            activeFile("f-1", "user-abc");

            mvc.perform(get("/files/f-1").with(user()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("f-1"))
                    .andExpect(jsonPath("$.versions.length()").value(1));
        }

        @Test
        void foreignFileIs404() throws Exception {
            activeFile("f-9", "someone-else");

            mvc.perform(get("/files/f-9").with(user()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void downloadRedirectsToPresignedUrl() throws Exception {
            activeFile("f-1", "user-abc");

            mvc.perform(get("/files/f-1/download").with(user()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            "https://s3.fake/download/user-abc/f-1?versionId=current"));
        }

        @Test
        void updateDescriptionPatches() throws Exception {
            activeFile("f-1", "user-abc");

            mvc.perform(patch("/files/f-1").with(user())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"Final"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("Final"));
        }

        @Test
        void newVersionTicketIssuedForActiveFile() throws Exception {
            activeFile("f-1", "user-abc");

            mvc.perform(post("/files/f-1/versions").with(user())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sizeBytes":2048}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.uploadUrl").value("https://s3.fake/upload/user-abc/f-1"));
        }

        @Test
        void deleteRemovesFile() throws Exception {
            activeFile("f-1", "user-abc");

            mvc.perform(delete("/files/f-1").with(user()))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/files/f-1").with(user()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Shares {

        @Test
        void createShareLinkReturnsToken() throws Exception {
            activeFile("f-1", "user-abc");
            Wiring.ids.willReturnTokens("tok-1");

            mvc.perform(post("/files/f-1/shares").with(user())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"ttlHours":24}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").value("tok-1"))
                    .andExpect(jsonPath("$.expiresAt").value("2026-07-14T12:00:00Z"));
        }

        @Test
        void publicResolveRedirectsWithoutAuth() throws Exception {
            activeFile("f-1", "user-abc");
            Wiring.ids.willReturnTokens("tok-1");
            mvc.perform(post("/files/f-1/shares").with(user())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"ttlHours":24}
                            """));

            mvc.perform(get("/public/shares/tok-1"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            "https://s3.fake/download/user-abc/f-1?versionId=current"));
        }

        @Test
        void unknownPublicTokenIs404() throws Exception {
            mvc.perform(get("/public/shares/nope"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void revokeDeletesLink() throws Exception {
            activeFile("f-1", "user-abc");
            Wiring.ids.willReturnTokens("tok-1");
            mvc.perform(post("/files/f-1/shares").with(user())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"ttlHours":24}
                            """));

            mvc.perform(delete("/shares/tok-1").with(user()))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/public/shares/tok-1"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Admin {

        @Test
        void adminEndpointsRejectNonAdmins() throws Exception {
            mvc.perform(get("/admin/files").with(user()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void adminListsAllFiles() throws Exception {
            activeFile("f-1", "user-abc");
            activeFile("f-9", "someone-else");

            mvc.perform(get("/admin/files").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void adminDeletesAnyFile() throws Exception {
            activeFile("f-9", "someone-else");

            mvc.perform(delete("/admin/files/f-9").with(admin()))
                    .andExpect(status().isNoContent());
        }
    }
}
