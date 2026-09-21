package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.user.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TokenIssuerTest {
    @Mock JwtService jwtService;
    @Mock TokenHasher tokenHasher;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks TokenIssuer tokenIssuer;

    private final User user = User.builder().id(UUID.randomUUID()).build();
    private MockHttpServletRequest request;
    private static final long EXPIRY_MS = 604_800_000L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenIssuer, "refreshTokenExpiryMs", EXPIRY_MS);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "sample-agent");
        when(jwtService.generateAccessToken(user)).thenReturn("access");
        when(jwtService.generateRefreshToken(user)).thenReturn("raw-refresh");
        when(tokenHasher.hash("raw-refresh")).thenReturn("stored-hash");
    }

    @Test
    void pairPersistsHashFamilyExpiryAndMetadataAndReturnsRawTokens() {
        UUID family = UUID.randomUUID();
        OffsetDateTime before = OffsetDateTime.now();
        TokenIssuer.IssuedTokens result = tokenIssuer.issueTokenPair(user, family, request);
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh");
        assertThat(result.refreshTokenExpiryMs()).isEqualTo(EXPIRY_MS);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        RefreshToken token = saved.getValue();
        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.getTokenHash()).isEqualTo("stored-hash").isNotEqualTo(result.refreshToken());
        assertThat(token.getFamilyId()).isEqualTo(family);
        assertThat(token.isRevoked()).isFalse();

        assertThat(token.getExpiresAt()).isBetween(before.plusSeconds(EXPIRY_MS / 1000), after.plusSeconds(EXPIRY_MS / 1000));
        assertThat(token.getLastUsedAt()).isBetween(before, after);
        assertThat(token.getUserAgent()).isEqualTo("sample-agent");
        assertThat(token.getIpAddress()).isEqualTo("192.0.2.10");
    }

    @Test
    void newSessionsHaveDifferentNonNullFamilies() {
        tokenIssuer.issueNewSession(user, request);
        tokenIssuer.issueNewSession(user, request);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(saved.capture());
        List<RefreshToken> list = saved.getAllValues();

        assertThat(list.get(0).getFamilyId()).isNotNull().isNotEqualTo(list.get(1).getFamilyId());
        assertThat(list.get(1).getFamilyId()).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " 203.0.113.8, 192.0.2.2"})
    void selectsForwardedOrRemoteAddress(String forwarded) {
        if (forwarded != null) {
            request.addHeader("X-Forwarded-For", forwarded);
        }

        tokenIssuer.issueTokenPair(user, UUID.randomUUID(), request);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getIpAddress()).isEqualTo(
                forwarded == null || forwarded.isBlank() ? "192.0.2.10" : "203.0.113.8"
        );
    }

    @Test
    void absentUserAgentIsStoredAsNull() {
        request.removeHeader("User-Agent");
        tokenIssuer.issueNewSession(user, request);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getUserAgent()).isNull();
    }
}