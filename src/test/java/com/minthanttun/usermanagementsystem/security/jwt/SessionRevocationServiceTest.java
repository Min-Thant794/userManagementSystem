package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.auth.RefreshTokenRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SessionRevocationServiceTest {
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks SessionRevocationService sessionRevocationService;

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void revokesForCorrectUserEvenWhenNoSessionsExist(int count) {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.revokedAllForUser(userId)).thenReturn(count);
        sessionRevocationService.revokeAllSessions(userId);
        verify(refreshTokenRepository).revokedAllForUser(userId);
        verifyNoMoreInteractions(refreshTokenRepository);
    }
}
