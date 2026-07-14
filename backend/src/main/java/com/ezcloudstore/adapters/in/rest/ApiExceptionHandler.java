package com.ezcloudstore.adapters.in.rest;

import com.ezcloudstore.domain.model.FileTooLargeException;
import com.ezcloudstore.domain.model.InvalidFileNameException;
import com.ezcloudstore.domain.model.InvalidShareTtlException;
import com.ezcloudstore.domain.model.ShareLinkExpiredException;
import com.ezcloudstore.domain.model.ShareLinkNotFoundException;
import com.ezcloudstore.domain.model.StoredFileNotFoundException;
import com.ezcloudstore.domain.model.UploadNotCompletedException;
import com.ezcloudstore.domain.model.UploadNotPendingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to RFC 9457 problem details. Not-found and
 * not-yours intentionally share a 404 (no resource enumeration).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({StoredFileNotFoundException.class, ShareLinkNotFoundException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", e.getMessage());
    }

    @ExceptionHandler(ShareLinkExpiredException.class)
    public ProblemDetail gone(ShareLinkExpiredException e) {
        return problem(HttpStatus.GONE, "Share Link Expired", e.getMessage());
    }

    @ExceptionHandler({UploadNotCompletedException.class, UploadNotPendingException.class})
    public ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Upload State Conflict", e.getMessage());
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ProblemDetail tooLarge(FileTooLargeException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File Too Large", e.getMessage());
    }

    @ExceptionHandler({InvalidFileNameException.class, InvalidShareTtlException.class})
    public ProblemDetail badRequest(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
