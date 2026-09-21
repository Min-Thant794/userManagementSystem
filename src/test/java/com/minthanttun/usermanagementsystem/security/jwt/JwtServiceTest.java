package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.user.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;


import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long-12345";
    private static final long ACCESS_TOKEN_EXPIRY_MS = 900_000L;
    private static final long REFRESH_TOKEN_EXPIRY_MS = 604_800_000L;
    private final JwtService jwtService = new JwtService(SECRET, ACCESS_TOKEN_EXPIRY_MS, REFRESH_TOKEN_EXPIRY_MS);
    private final User user = User.builder().id(UUID.randomUUID()).username("testuser").role(Role.ADMIN).build();

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
    }

    @ParameterizedTest
    @ValueSource(strings = {"access", "refresh"})
    void generatedTokensContainIdentityTypeAndConfiguredLifetime(String type) {
        long before = System.currentTimeMillis();
        String token = type.equals("access") ? jwtService.generateAccessToken(user) : jwtService.generateRefreshToken(user);
        long after = System.currentTimeMillis();
        Claims payload = claims(token); // Independent parser checks actual contents.
        assertThat(payload.getSubject()).isEqualTo(user.getId().toString());
        assertThat(payload.get("username", String.class)).isEqualTo("testuser");
        assertThat(payload.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(payload.get("type", String.class)).isEqualTo(type);
        // JWT dates have second precision; these configured lifetimes are whole seconds.
        assertThat(payload.getIssuedAt().getTime()).isBetween(before - 999, after);
        assertThat(payload.getExpiration().getTime() - payload.getIssuedAt().getTime())
                .isEqualTo(type.equals("access") ? ACCESS_TOKEN_EXPIRY_MS : REFRESH_TOKEN_EXPIRY_MS);
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
        assertThat(jwtService.extractTokenType(token)).isEqualTo(type);
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {" ", "not-a-jwt", "abc.def.ghi"}
    )
    void invalidInputIsNotValid(String input) {
        assertThat(jwtService.isTokenValid(input)).isFalse();
    }

    @Test
    void expiredTokenIsInvalidAndReportedExpiredWithoutSleeping() {
        String expired = new JwtService(SECRET, -60_000, REFRESH_TOKEN_EXPIRY_MS)
                .generateAccessToken(user);
        assertThat(jwtService.isTokenValid(expired)).isFalse();
        assertThat(jwtService.isTokenExpired(expired)).isTrue();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String token = new JwtService(
                "another-secret-key-at-least-32-bytes-long-6789",
                ACCESS_TOKEN_EXPIRY_MS,
                REFRESH_TOKEN_EXPIRY_MS
        ).generateAccessToken(user);
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void changedPayloadWithOriginalSignatureIsRejected() {
        String[] parts = jwtService.generateAccessToken(user).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("testuser", "anothertestuser").getBytes(StandardCharsets.UTF_8));
        assertThat(jwtService.isTokenValid(String.join(".", parts))).isFalse();
    }

    @Test
    void expiryCheckCurrentlyThrowsForMalformedInput() {
        //characterizes the existing contract. Call isTokenValid before this method.
        assertThatThrownBy(() -> jwtService.isTokenExpired("not-a-jwt")).isInstanceOf(JwtException.class);
    }
}
