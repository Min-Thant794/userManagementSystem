package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.auth.dto.SessionResponse;
import com.minthanttun.usermanagementsystem.common.exception.ResourceNotFoundException;
import com.minthanttun.usermanagementsystem.security.jwt.TokenHasher;
import com.minthanttun.usermanagementsystem.security.session.*;
import com.minthanttun.usermanagementsystem.user.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SessionServiceTest {
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserAgentParsingService userAgentParsingService;
    @Mock GeoLocationService geoLocationService;
    @Mock TokenHasher tokenHasher;
    @InjectMocks SessionService sessionService;
    private final UUID userId = UUID.randomUUID();

    private RefreshToken session(long id, String hash) {
    return RefreshToken.builder().id(id).user(User.builder().id(userId).build()).tokenHash(hash)
                .userAgent("sample-agent").ipAddress("203.0.113.7")
                .createdAt(OffsetDateTime.now().minusDays(1)).lastUsedAt(OffsetDateTime.now().minusMinutes(1)).build();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void revokeOwnedSessionIncludingAlreadyRevokedCase(int updatedRows) {
        RefreshToken token = session(1, "hash");
        when(refreshTokenRepository.findByIdAndUser_Id(1L, userId)).thenReturn(Optional.of(token));
        when(refreshTokenRepository.revokeIfActive(1L)).thenReturn(updatedRows);
        sessionService.revokeSession(1L,userId);
        verify(refreshTokenRepository).revokeIfActive(1L);
    }

    @Test
    void missingOrNotOwnedSessionIsNotRevoked() {
        when(refreshTokenRepository.findByIdAndUser_Id(1L, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sessionService.revokeSession(1L, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(refreshTokenRepository, never()).revokeIfActive(anyLong());
        //The ownership predicate itself requires a repository integration test.
    }

    @Test
    void revokesOthersButExcludesCurrentSession() {
        when(tokenHasher.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(session(7, "hash")));
        when(refreshTokenRepository.revokeAllExcept(userId, 7L)).thenReturn(3);
        assertThat(sessionService.revokeAllOtherSessions(userId, "raw")).isEqualTo(3);
        verify(refreshTokenRepository, never()).revokedAllForUser(any());
    }

    @Test
    void noCurrentTokenRevokesAllSessions() {
        when(refreshTokenRepository.revokedAllForUser(userId)).thenReturn(2);
        assertThat(sessionService.revokeAllOtherSessions(userId, null)).isEqualTo(2);
        verifyNoInteractions(tokenHasher);
        verify(refreshTokenRepository, never()).revokeAllExcept(any(), anyLong());
    }

    @Test
    void unknownCurrentTokenIsRejected() {
        when(tokenHasher.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sessionService.revokeAllOtherSessions(userId, "raw"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(refreshTokenRepository, never()).revokeAllExcept(any(), anyLong());
        verify(refreshTokenRepository, never()).revokedAllForUser(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"raw"})
    void listingMapsMetadataAndCurrentMarker(String currentRaw) {
        RefreshToken first = session(1, "first-hash");
        RefreshToken second = session(2, "second-hash");
        if (currentRaw != null) {
            when(tokenHasher.hash("raw")).thenReturn("first-hash");
        }

        when(refreshTokenRepository.findAllByUser_IdAndRevokedFalseAndExpiresAtAfter(eq(userId), any())).thenReturn(List.of(first, second));
        when(userAgentParsingService.describeDevice("sample-agent")).thenReturn("Chrome on macOS");
        when(geoLocationService.describeLocation("203.0.113.7")).thenReturn("Example city");
        OffsetDateTime before = OffsetDateTime.now();

        List<SessionResponse> result = sessionService.listSessions(userId, currentRaw);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).device()).isEqualTo("Chrome on macOS");
        assertThat(result.get(0).location()).isEqualTo("Example city");
        assertThat(result.get(0).createdAt()).isEqualTo(first.getCreatedAt());
        assertThat(result.get(0).lastUsedAt()).isEqualTo(first.getLastUsedAt());
        assertThat(result.get(0).current()).isEqualTo(currentRaw != null);
        assertThat(result.get(1).current()).isFalse();
        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(refreshTokenRepository).findAllByUser_IdAndRevokedFalseAndExpiresAtAfter(eq(userId), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, OffsetDateTime.now());
        if (currentRaw == null) {
            verifyNoInteractions(tokenHasher);
        }
    }

    @Test void noActiveSessionsReturnsEmptyList() {
        when(refreshTokenRepository.findAllByUser_IdAndRevokedFalseAndExpiresAtAfter(eq(userId), any())).thenReturn(List.of());
        assertThat(sessionService.listSessions(userId, null)).isEmpty();
        verifyNoInteractions(userAgentParsingService, geoLocationService, tokenHasher);
    }
}
