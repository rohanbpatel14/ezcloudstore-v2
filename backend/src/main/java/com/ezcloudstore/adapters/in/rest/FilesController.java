package com.ezcloudstore.adapters.in.rest;

import com.ezcloudstore.adapters.in.rest.dto.Requests;
import com.ezcloudstore.adapters.in.rest.dto.Responses.FileDetailsResponse;
import com.ezcloudstore.adapters.in.rest.dto.Responses.FileResponse;
import com.ezcloudstore.adapters.in.rest.dto.Responses.ShareLinkResponse;
import com.ezcloudstore.adapters.in.rest.dto.Responses.UploadTicketResponse;
import com.ezcloudstore.application.CompleteUploadUseCase;
import com.ezcloudstore.application.CreateShareLinkUseCase;
import com.ezcloudstore.application.DeleteFileUseCase;
import com.ezcloudstore.application.GetDownloadUrlUseCase;
import com.ezcloudstore.application.GetFileUseCase;
import com.ezcloudstore.application.InitiateUploadUseCase;
import com.ezcloudstore.application.InitiateVersionUploadUseCase;
import com.ezcloudstore.application.ListFilesUseCase;
import com.ezcloudstore.application.UpdateDescriptionUseCase;
import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/files")
public class FilesController {

    private final InitiateUploadUseCase initiateUpload;
    private final CompleteUploadUseCase completeUpload;
    private final ListFilesUseCase listFiles;
    private final GetFileUseCase getFile;
    private final GetDownloadUrlUseCase getDownloadUrl;
    private final UpdateDescriptionUseCase updateDescription;
    private final InitiateVersionUploadUseCase initiateVersionUpload;
    private final DeleteFileUseCase deleteFile;
    private final CreateShareLinkUseCase createShareLink;

    public FilesController(InitiateUploadUseCase initiateUpload,
                           CompleteUploadUseCase completeUpload,
                           ListFilesUseCase listFiles,
                           GetFileUseCase getFile,
                           GetDownloadUrlUseCase getDownloadUrl,
                           UpdateDescriptionUseCase updateDescription,
                           InitiateVersionUploadUseCase initiateVersionUpload,
                           DeleteFileUseCase deleteFile,
                           CreateShareLinkUseCase createShareLink) {
        this.initiateUpload = initiateUpload;
        this.completeUpload = completeUpload;
        this.listFiles = listFiles;
        this.getFile = getFile;
        this.getDownloadUrl = getDownloadUrl;
        this.updateDescription = updateDescription;
        this.initiateVersionUpload = initiateVersionUpload;
        this.deleteFile = deleteFile;
        this.createShareLink = createShareLink;
    }

    private static OwnerId owner(Jwt jwt) {
        return OwnerId.of(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadTicketResponse initiate(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody Requests.InitiateUpload request) {
        return UploadTicketResponse.from(initiateUpload.handle(owner(jwt), request.name(),
                request.description(), request.sizeBytes(), request.contentType()));
    }

    @PostMapping("/{id}/complete")
    public FileResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return FileResponse.from(completeUpload.handle(owner(jwt), FileId.of(id)));
    }

    @GetMapping
    public List<FileResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return listFiles.handle(owner(jwt)).stream().map(FileResponse::from).toList();
    }

    @GetMapping("/{id}")
    public FileDetailsResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return FileDetailsResponse.from(getFile.handle(owner(jwt), FileId.of(id)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                         @RequestParam(required = false) String versionId) {
        URI url = getDownloadUrl.handle(owner(jwt), FileId.of(id), versionId);
        return ResponseEntity.status(HttpStatus.FOUND).location(url).build();
    }

    @PatchMapping("/{id}")
    public FileResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                               @Valid @RequestBody Requests.UpdateFile request) {
        return FileResponse.from(updateDescription.handle(owner(jwt), FileId.of(id), request.description()));
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadTicketResponse newVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                           @Valid @RequestBody Requests.NewVersion request) {
        return UploadTicketResponse.from(
                initiateVersionUpload.handle(owner(jwt), FileId.of(id), request.sizeBytes()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        deleteFile.handle(owner(jwt), FileId.of(id));
    }

    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareLinkResponse share(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                   @Valid @RequestBody Requests.CreateShare request) {
        return ShareLinkResponse.from(createShareLink.handle(owner(jwt), FileId.of(id),
                Duration.ofHours(request.ttlHours())));
    }
}
