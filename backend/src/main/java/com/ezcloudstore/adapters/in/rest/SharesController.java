package com.ezcloudstore.adapters.in.rest;

import com.ezcloudstore.application.RevokeShareLinkUseCase;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.ShareToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shares")
public class SharesController {

    private final RevokeShareLinkUseCase revokeShareLink;

    public SharesController(RevokeShareLinkUseCase revokeShareLink) {
        this.revokeShareLink = revokeShareLink;
    }

    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        revokeShareLink.handle(OwnerId.of(jwt.getSubject()), ShareToken.of(token));
    }
}
