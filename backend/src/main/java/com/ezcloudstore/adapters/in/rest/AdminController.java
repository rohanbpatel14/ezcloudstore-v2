package com.ezcloudstore.adapters.in.rest;

import com.ezcloudstore.adapters.in.rest.dto.Responses.FileResponse;
import com.ezcloudstore.application.AdminDeleteFileUseCase;
import com.ezcloudstore.application.AdminListFilesUseCase;
import com.ezcloudstore.domain.model.FileId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Route-level guard: SecurityConfig requires ROLE_admin for /admin/**
 * (mapped from the Cognito "admin" group claim).
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminListFilesUseCase adminListFiles;
    private final AdminDeleteFileUseCase adminDeleteFile;

    public AdminController(AdminListFilesUseCase adminListFiles, AdminDeleteFileUseCase adminDeleteFile) {
        this.adminListFiles = adminListFiles;
        this.adminDeleteFile = adminDeleteFile;
    }

    @GetMapping("/files")
    public List<FileResponse> listAll() {
        return adminListFiles.handle().stream().map(FileResponse::from).toList();
    }

    @DeleteMapping("/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        adminDeleteFile.handle(FileId.of(id));
    }
}
