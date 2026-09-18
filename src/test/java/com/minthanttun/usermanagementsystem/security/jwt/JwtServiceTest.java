package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.user.Role;
import com.minthanttun.usermanagementsystem.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET =
            "test-secret-key-that-is-at-least-32-bytes-long-12345";

    private static final long ACCESS_TOKEN_EXPIRY_MS = 15 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                TEST_SECRET,
                ACCESS_TOKEN_EXPIRY_MS,
                REFRESH_TOKEN_EXPIRY_MS
        );
    }

    @Test
    void generateAccessToken_shouldGenerateValidToken() {

        //arrange
        User user = createTestUser();

        //act
        String token = jwtService.generateAccessToken(user);

        //assert
        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    private User createTestUser() {
        User user = new User();

        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setRole(Role.USER);

        return user;
    }
}
