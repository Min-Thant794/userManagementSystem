package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.auth.dto.SessionResponse;
import com.minthanttun.usermanagementsystem.common.exception.ResourceNotFoundException;
import com.minthanttun.usermanagementsystem.security.jwt.TokenHasher;
import com.minthanttun.usermanagementsystem.security.session.GeoLocationService;
import com.minthanttun.usermanagementsystem.security.session.UserAgentParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAgentParsingService userAgentParsingService;
    private final GeoLocationService geoLocationService;
    private final TokenHasher tokenHasher;

    @Transactional
    public void revokeSession(Long sessionId, UUID userId) {
        RefreshToken token = refreshTokenRepository.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        refreshTokenRepository.revokeIfActive(token.getId());
    }

    @Transactional
    public int revokeAllOtherSessions(UUID userId, String currentRawRefreshToken) {
        if (currentRawRefreshToken == null) {
            //No current session to protect - just revoke everything.
            return refreshTokenRepository.revokedAllForUser(userId);
        }

        String currentHash = tokenHasher.hash(currentRawRefreshToken);
        RefreshToken currentToken = refreshTokenRepository.findByTokenHash(currentHash)
                .orElseThrow(() -> new ResourceNotFoundException("Current session not found"));

        return refreshTokenRepository.revokeAllExcept(userId, currentToken.getId());
    }

    public List<SessionResponse> listSessions(UUID userId, String currentRawRefreshToken) {
        String currentHash = currentRawRefreshToken != null ? tokenHasher.hash(currentRawRefreshToken) : null;

        List<RefreshToken> activeSessions = refreshTokenRepository
                .findAllByUser_IdAndRevokedFalseAndExpiresAtAfter(userId, OffsetDateTime.now());

        return activeSessions.stream()
                .map(token -> toResponse(token, currentHash))
                .toList();
    }

    private SessionResponse toResponse(RefreshToken token, String currentHash) {
        return new SessionResponse(
                token.getId(),
                userAgentParsingService.describeDevice(token.getUserAgent()),
                geoLocationService.describeLocation(token.getIpAddress()),
                token.getLastUsedAt(),
                token.getCreatedAt(),
                token.getTokenHash().equals(currentHash)
        );
    }
}
