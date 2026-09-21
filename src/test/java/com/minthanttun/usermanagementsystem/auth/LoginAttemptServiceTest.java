package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoginAttemptServiceTest {
    @Mock UserRepository userRepository;
    @InjectMocks LoginAttemptService loginAttemptService;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void incrementsBelowThresholdWithoutLocking(int previous) {
        User user = User.builder()
                .username("testuser")
                .failedLoginAttempts(previous)
                .build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        loginAttemptService.recordFailedAttempt("testuser");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(previous + 1);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 9})
    void fifthAdLaterAttemptsSetOrExtendLock(int previous) {
        User user = User.builder()
                .username("testuser")
                .failedLoginAttempts(previous)
                .lockedUntil(previous >= 5 ? OffsetDateTime.now().plusMinutes(2) : null)
                .build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        OffsetDateTime before = OffsetDateTime.now();
        loginAttemptService.recordFailedAttempt("testuser");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(previous + 1);
        assertThat(user.getLockedUntil()).isBetween(before.plusMinutes(15), OffsetDateTime.now().plusMinutes(15));
        verify(userRepository).save(user);
    }

    @Test
    void unknownUsernameDoesNotSave() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        loginAttemptService.recordFailedAttempt("missing");
        verify(userRepository, never()).save(any());
    }
}
