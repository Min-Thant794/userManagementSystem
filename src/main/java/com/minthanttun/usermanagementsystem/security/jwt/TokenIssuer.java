package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.auth.RefreshToken;
import com.minthanttun.usermanagementsystem.auth.RefreshTokenRepository;
import com.minthanttun.usermanagementsystem.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtService jwtService;
    private final TokenHasher tokenHasher;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    public record IssuedTokens(String accessToken, String refreshToken, long refreshTokenExpiryMs) {}

    @Transactional
    public IssuedTokens issueNewSession(User user) {
        return issueTokenPair(user, UUID.randomUUID());
    }

    @Transactional
    public IssuedTokens issueTokenPair(User user, UUID familyId) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        RefreshToken tokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHasher.hash(refreshToken))
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshTokenExpiryMs / 1000))
                .revoked(false)
                .familyId(familyId)
                .build();
        refreshTokenRepository.save(tokenEntity);

        return new IssuedTokens(accessToken, refreshToken, refreshTokenExpiryMs);
    }
}
