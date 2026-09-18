package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.auth.dto.LoginRequest;
import com.minthanttun.usermanagementsystem.auth.dto.SignupRequest;
import com.minthanttun.usermanagementsystem.common.exception.*;
import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.security.jwt.TokenHasher;
import com.minthanttun.usermanagementsystem.security.jwt.TokenIssuer;
import com.minthanttun.usermanagementsystem.user.AccountStatus;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private EmailVerificationService emailVerificationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                loginAttemptService,
                userRepository,
                passwordEncoder,
                authenticationManager,
                tokenHasher,
                refreshTokenRepository,
                tokenIssuer,
                emailVerificationService
        );
    }

    @Test
    void signup_shouldCreateUserSuccessfully() {
        //arrange
        SignupRequest request = new SignupRequest(
                "testuser",
                "test@example.com",
                "+6594624174",
                "Password123!"
        );

        User savedUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .phoneNumber("+6594624174")
                .passwordHash("encoded-password")
                .build();

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(false);

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumber("+6594624174"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password123!"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        //Act
        User result = authService.signup(request);

        //Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User userToSave = userCaptor.getValue();

        assertThat(userToSave.getUsername())
                .isEqualTo("testuser");

        assertThat(userToSave.getEmail())
                .isEqualTo("test@example.com");

        assertThat(userToSave.getPhoneNumber())
                .isEqualTo("+6594624174");

        assertThat(userToSave.getPasswordHash())
                .isEqualTo("encoded-password");

        assertThat(userToSave.getPasswordHash())
                .isNotEqualTo("Password123!");
    }

    @Test
    void signup_shouldRejectDuplicateUsername() {
        // Arrange
        SignupRequest request = new SignupRequest(
                "testuser",
                "test@example.com",
                "+6594624174",
                "Password123!"
        );

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username is already taken");

        // Verify nothing was created
        verify(userRepository, never())
                .existsByEmail(anyString());

        verify(userRepository, never())
                .existsByPhoneNumber(anyString());

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(userRepository, never())
                .save(any(User.class));

        verify(emailVerificationService, never())
                .generateVerificationEmail(any(User.class), anyString());
    }

    @Test
    void signup_shouldRejectDuplicateEmail() {
        //Arrange
        SignupRequest request = new SignupRequest(
                "testuser",
                "test@example.com",
                "+6594624174",
                "Password123!"
        );

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(false);

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(true);

        //Act & Assert
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email is already registered");

        //Verify phone is not checked after duplicate email is detected
        verify(userRepository, never())
                .existsByPhoneNumber(anyString());

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(userRepository, never())
                .save(any(User.class));

        verify(emailVerificationService, never())
                .generateVerificationEmail(any(User.class), anyString());
    }

    @Test
    void signup_shouldRejectDuplicatePhoneNumber() {
        //Arrange
        SignupRequest request = new SignupRequest(
                "testuser",
                "test@example.com",
                "+6594624174",
                "Password123!"
        );

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(false);

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumber("+6594624174"))
                .thenReturn(true);

        //Act & Assert
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Phone number is already registered");

        //verify password is never processed
        verify(passwordEncoder, never())
                .encode(anyString());

        //verify user is never created
        verify(userRepository, never())
                .save(any(User.class));

        //verify verification email is never sent
        verify(emailVerificationService, never())
                .generateVerificationEmail(any(User.class), anyString());
    }

    @Test
    void signup_shouldEncodePasswordBeforeSaving() {
        //Arrange
        SignupRequest request = new SignupRequest(
                "testuser",
                "test@example.com",
                "+6594624174",
                "Password123!"
        );

        User savedUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .phoneNumber("+6594624174")
                .passwordHash("encoded-password")
                .build();

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(false);

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumber("+6594624174"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password123!"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        //Act
        authService.signup(request);

        //Assert
        verify(passwordEncoder)
                .encode("Password123!");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getPasswordHash())
                .isEqualTo("encoded-password");
    }

    @Test
    void login_shouldRejectUnverifiedEmail() {
        //Arrange
        LoginRequest request = new LoginRequest(
                "testuser",
                "Password123!"
        );

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .emailVerified(false)
                .build();

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        //Act & Assert
        assertThatThrownBy(() -> authService.login(request, mock(HttpServletRequest.class)))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessage("Please verify your email address before logging in");

        //Authentication must never happen
        verify(authenticationManager, never())
                .authenticate(any());

        verify(loginAttemptService, never())
                .recordFailedAttempt(anyString());

        verify(tokenIssuer, never())
                .issueNewSession(any(User.class), any(HttpServletRequest.class));
    }

    @Test
    void login_shouldAuthenticateSuccessfullyWithUsername() {
        //Arrange
        LoginRequest request = new LoginRequest(
                "testuser",
                "Password123!"
        );

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .emailVerified(true)
                .failedLoginAttempts(0)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user);

        TokenIssuer.IssuedTokens issuedTokens = mock(TokenIssuer.IssuedTokens.class);

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        when(authenticationManager.authenticate(any()))
                .thenReturn(
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        )
                );

        when(tokenIssuer.issueNewSession(user, httpRequest)).thenReturn(issuedTokens);

        //Act
        TokenIssuer.IssuedTokens result = authService.login(request, httpRequest);

        //Assert
        assertThat(result).isSameAs(issuedTokens);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        verify(tokenIssuer).issueNewSession(user, httpRequest);

        verify(loginAttemptService, never()).recordFailedAttempt(anyString());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_shouldRejectInvalidCredentialsAndRecordFailedAttempt() {
        //Arrange
        LoginRequest request = new LoginRequest(
                "testuser",
                "WrongPassword123!"
        );

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .emailVerified(true)
                .build();

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        //Act & Assert
        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");

        //Failed attempt must be recorded
        verify(loginAttemptService)
                .recordFailedAttempt("testuser");

        //No token should be issued
        verify(tokenIssuer, never())
                .issueNewSession(any(User.class), any(HttpServletRequest.class));
    }

    @Test
    void login_shouldRejectLockedAccount() {
        //Arrange
        LoginRequest request = new LoginRequest(
                "testuser",
                "Password123!"
        );

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .emailVerified(true)
                .build();

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        when(authenticationManager.authenticate(any()))
                .thenThrow(new LockedException("Account locked"));

        //Act & Assert
        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessage("This account has been suspended or is temporarily locked");

        //Failed-attempt counter should NOT be recorded here
        verify(loginAttemptService, never()).recordFailedAttempt(anyString());

        //No token should be issued
        verify(tokenIssuer, never()).issueNewSession(any(User.class), any(HttpServletRequest.class));
    }

    @Test
    void login_shouldAuthenticateSuccessfullyWithEmail() {
        //Arrange
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "Password123!"
        );

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .emailVerified(true)
                .failedLoginAttempts(0)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user);

        TokenIssuer.IssuedTokens issuedTokens = mock(TokenIssuer.IssuedTokens.class);

        when(userRepository.findByUsername("test@example.com")).thenReturn(Optional.empty());

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        when(authenticationManager.authenticate(any()))
                .thenReturn(
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        )
                );

        when(tokenIssuer.issueNewSession(user, httpRequest)).thenReturn(issuedTokens);

        //Act
        TokenIssuer.IssuedTokens result = authService.login(request, httpRequest);

        //Assert
        assertThat(result).isSameAs(issuedTokens);

        //Authentication must use the user's username, not their email.
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor = ArgumentCaptor.forClass(
                UsernamePasswordAuthenticationToken.class
        );

        verify(authenticationManager).authenticate(captor.capture());

        assertThat(captor.getValue().getPrincipal()).isEqualTo("testuser");

        assertThat(captor.getValue().getCredentials()).isEqualTo("Password123!");

        verify(tokenIssuer).issueNewSession(user, httpRequest);
    }

    @Test
    void login_shouldResetFailedAttemptsAfterSuccessfulLogin() {
        //Arrange
        LoginRequest request = new LoginRequest(
                "testuser",
                "Password123!"
        );

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .emailVerified(true)
                .failedLoginAttempts(3)
                .lockedUntil(OffsetDateTime.now().plusMinutes(10))
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user);

        TokenIssuer.IssuedTokens issuedTokens = mock(TokenIssuer.IssuedTokens.class);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                )
        );

        when(tokenIssuer.issueNewSession(user, httpRequest)).thenReturn(issuedTokens);

        //Act
        authService.login(request, httpRequest);

        //Assert
        assertThat(user.getFailedLoginAttempts()).isZero();

        assertThat(user.getLockedUntil()).isNull();

        verify(userRepository).save(user);

        verify(tokenIssuer).issueNewSession(user, httpRequest);
    }

    @Test
    void refresh_shouldIssueNewTokenPairSuccessfully() {
        //Arrange
        String rawRefreshToken = "refresh-token";
        String tokenHash = "hashed-refresh-token";

        Long tokenId = 1L;
        UUID familyId = UUID.randomUUID();

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .status(AccountStatus.ACTIVE)
                .build();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(tokenId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .user(user)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        TokenIssuer.IssuedTokens issuedTokens = mock(TokenIssuer.IssuedTokens.class);

        when(tokenHasher.hash(rawRefreshToken)).thenReturn(tokenHash);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

        when(refreshTokenRepository.revokeIfActive(tokenId)).thenReturn(1);

        when(tokenIssuer.issueTokenPair(
                user,
                familyId,
                httpRequest
        )).thenReturn(issuedTokens);

        //Act
        TokenIssuer.IssuedTokens result = authService.refresh(rawRefreshToken, httpRequest);

        //Assert
        assertThat(result).isSameAs(issuedTokens);

        verify(tokenHasher).hash(rawRefreshToken);

        verify(refreshTokenRepository).findByTokenHash(tokenHash);

        verify(refreshTokenRepository).revokeIfActive(anyLong());

        verify(tokenIssuer).issueTokenPair(user, familyId, httpRequest);

        verify(refreshTokenRepository, never()).revokeFamily(any(UUID.class));
    }

    @Test
    void refresh_shouldRejectExpiredRefreshToken() {
        //Arrange
        String rawRefreshToken = "expired-refresh-token";
        String tokenHash = "hashed-expired-token";

        Long tokenId = 1L;

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .status(AccountStatus.ACTIVE)
                .build();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(tokenId)
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .revoked(false)
                .build();

        when(tokenHasher.hash(rawRefreshToken)).thenReturn(tokenHash);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

        //Act & Assert
        assertThatThrownBy(() -> authService.refresh(
                rawRefreshToken,
                mock(HttpServletRequest.class)
        )).isInstanceOf(InvalidCredentialsException.class).hasMessage("Refresh token has expired");

        //Expired token must not be rotated
        verify(refreshTokenRepository, never()).revokeIfActive(anyLong());

        verify(tokenIssuer, never()).issueTokenPair(
                any(User.class),
                any(UUID.class),
                any(HttpServletRequest.class)
        );
    }

    @Test
    void refresh_shouldRejectInvalidRefreshToken() {
        //Arrange
        String rawRefreshToken = "invalid-refresh-token";
        String tokenHash = "hashed-invalid-token";

        when(tokenHasher.hash(rawRefreshToken)).thenReturn(tokenHash);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        //Act & Assert
        assertThatThrownBy(() -> authService.refresh(
                rawRefreshToken,
                mock(HttpServletRequest.class)
        ))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid refresh token");

        verify(tokenHasher).hash(rawRefreshToken);

        verify(refreshTokenRepository).findByTokenHash(tokenHash);

        verify(refreshTokenRepository, never()).revokeIfActive(anyLong());

        verify(refreshTokenRepository, never()).revokeFamily(any(UUID.class));

        verify(tokenIssuer, never()).issueTokenPair(
                any(User.class),
                any(UUID.class),
                any(HttpServletRequest.class)
        );
    }

    @Test
    void refresh_shouldDetectRefreshTokenReuseAndRevokeFamily() {
        //Arrange
        String rawRefreshToken = "reused-refresh-token";
        String tokenHash = "hash-reused-token";

        Long tokenId = 1L;
        UUID familyId = UUID.randomUUID();

        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .status(AccountStatus.ACTIVE)
                .build();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(tokenId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .user(user)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(true)
                .build();

        when(tokenHasher.hash(rawRefreshToken)).thenReturn(tokenHash);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

        //Token was already consumed/revoked
        when(refreshTokenRepository.revokeIfActive(tokenId)).thenReturn(0);

        //Act & Assert
        assertThatThrownBy(() -> authService.refresh(
                rawRefreshToken,
                mock(HttpServletRequest.class)
        ))
                .isInstanceOf(RefreshTokenReuseException.class)
                .hasMessage("This session has been compromised or reused. Please log in again");

        //Entire token family must be revoked
        verify(refreshTokenRepository).revokeFamily(familyId);

        //Now new tokens should be issued
        verify(tokenIssuer, never())
                .issueTokenPair(
                        any(User.class),
                        any(UUID.class),
                        any(HttpServletRequest.class)
                );
    }
}
