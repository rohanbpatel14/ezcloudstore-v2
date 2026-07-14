package com.ezcloudstore.config;

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
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;
import com.ezcloudstore.domain.port.IdGenerator;
import com.ezcloudstore.domain.port.ShareLinkRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Use cases stay framework-free; Spring wiring happens only here. */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public InitiateUploadUseCase initiateUpload(FileRepository files, FileStorage storage,
                                                IdGenerator ids, Clock clock) {
        return new InitiateUploadUseCase(files, storage, ids, clock);
    }

    @Bean
    public CompleteUploadUseCase completeUpload(FileRepository files, FileStorage storage, Clock clock) {
        return new CompleteUploadUseCase(files, storage, clock);
    }

    @Bean
    public ListFilesUseCase listFiles(FileRepository files) {
        return new ListFilesUseCase(files);
    }

    @Bean
    public GetFileUseCase getFile(FileRepository files) {
        return new GetFileUseCase(files);
    }

    @Bean
    public GetDownloadUrlUseCase getDownloadUrl(FileRepository files, FileStorage storage) {
        return new GetDownloadUrlUseCase(files, storage);
    }

    @Bean
    public UpdateDescriptionUseCase updateDescription(FileRepository files, Clock clock) {
        return new UpdateDescriptionUseCase(files, clock);
    }

    @Bean
    public InitiateVersionUploadUseCase initiateVersionUpload(FileRepository files, FileStorage storage) {
        return new InitiateVersionUploadUseCase(files, storage);
    }

    @Bean
    public DeleteFileUseCase deleteFile(FileRepository files, ShareLinkRepository shareLinks,
                                        FileStorage storage) {
        return new DeleteFileUseCase(files, shareLinks, storage);
    }

    @Bean
    public CreateShareLinkUseCase createShareLink(FileRepository files, ShareLinkRepository shareLinks,
                                                  IdGenerator ids, Clock clock) {
        return new CreateShareLinkUseCase(files, shareLinks, ids, clock);
    }

    @Bean
    public RevokeShareLinkUseCase revokeShareLink(ShareLinkRepository shareLinks) {
        return new RevokeShareLinkUseCase(shareLinks);
    }

    @Bean
    public ResolveShareLinkUseCase resolveShareLink(ShareLinkRepository shareLinks, FileRepository files,
                                                    FileStorage storage, Clock clock) {
        return new ResolveShareLinkUseCase(shareLinks, files, storage, clock);
    }

    @Bean
    public AdminListFilesUseCase adminListFiles(FileRepository files) {
        return new AdminListFilesUseCase(files);
    }

    @Bean
    public AdminDeleteFileUseCase adminDeleteFile(FileRepository files, ShareLinkRepository shareLinks,
                                                  FileStorage storage) {
        return new AdminDeleteFileUseCase(files, shareLinks, storage);
    }
}
