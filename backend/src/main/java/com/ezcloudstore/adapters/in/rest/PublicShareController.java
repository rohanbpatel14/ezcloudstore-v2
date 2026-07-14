package com.ezcloudstore.adapters.in.rest;

import com.ezcloudstore.application.ResolveShareLinkUseCase;
import com.ezcloudstore.domain.model.ShareToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only unauthenticated API surface: share-token resolution.
 * Redirects to a short-lived presigned S3 GET.
 */
@RestController
@RequestMapping("/public/shares")
public class PublicShareController {

    private final ResolveShareLinkUseCase resolveShareLink;

    public PublicShareController(ResolveShareLinkUseCase resolveShareLink) {
        this.resolveShareLink = resolveShareLink;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Void> resolve(@PathVariable String token) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(resolveShareLink.handle(ShareToken.of(token)))
                .build();
    }
}
