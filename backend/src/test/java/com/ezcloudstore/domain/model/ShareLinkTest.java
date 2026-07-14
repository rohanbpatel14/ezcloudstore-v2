package com.ezcloudstore.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ShareLinkTest {

    private static final ShareToken TOKEN = ShareToken.of("tok-xyz");
    private static final FileId FILE = FileId.of("f-123");
    private static final OwnerId OWNER = OwnerId.of("user-abc");
    private static final Instant T0 = Instant.parse("2026-07-13T12:00:00Z");

    @Test
    void createSetsExpiryFromTtl() {
        ShareLink link = ShareLink.create(TOKEN, FILE, OWNER, T0, Duration.ofHours(24));

        assertThat(link.token()).isEqualTo(TOKEN);
        assertThat(link.fileId()).isEqualTo(FILE);
        assertThat(link.owner()).isEqualTo(OWNER);
        assertThat(link.createdAt()).isEqualTo(T0);
        assertThat(link.expiresAt()).isEqualTo(T0.plus(Duration.ofHours(24)));
    }

    @Test
    void createRejectsNonPositiveTtl() {
        assertThatExceptionOfType(InvalidShareTtlException.class)
                .isThrownBy(() -> ShareLink.create(TOKEN, FILE, OWNER, T0, Duration.ZERO));
    }

    @Test
    void createRejectsTtlBeyondSevenDays() {
        assertThatExceptionOfType(InvalidShareTtlException.class)
                .isThrownBy(() -> ShareLink.create(TOKEN, FILE, OWNER, T0, Duration.ofDays(7).plusSeconds(1)));
    }

    @Test
    void sevenDaysExactlyIsAllowed() {
        ShareLink link = ShareLink.create(TOKEN, FILE, OWNER, T0, Duration.ofDays(7));

        assertThat(link.expiresAt()).isEqualTo(T0.plus(Duration.ofDays(7)));
    }

    @Test
    void isExpiredComparesAgainstExpiry() {
        ShareLink link = ShareLink.create(TOKEN, FILE, OWNER, T0, Duration.ofHours(1));

        assertThat(link.isExpired(T0.plus(Duration.ofMinutes(59)))).isFalse();
        assertThat(link.isExpired(T0.plus(Duration.ofHours(1)))).isTrue();
        assertThat(link.isExpired(T0.plus(Duration.ofHours(2)))).isTrue();
    }
}
