package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.auth.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void revokeAllSessions(UUID userId) {
        int revoked = refreshTokenRepository.revokedAllForUser(userId);
        log.info("Revoked {} active session(s) for user {}", revoked, userId);
    }
}
