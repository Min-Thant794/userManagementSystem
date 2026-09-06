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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final LoginAttemptService loginAttemptService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenHasher tokenHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenIssuer tokenIssuer;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public User signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new DuplicateResourceException("Phone number is already registered");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User saved = userRepository.save(user);

        emailVerificationService.generateVerificationEmail(saved, saved.getEmail());

        return saved;
    }

    @Transactional
    public TokenIssuer.IssuedTokens login(LoginRequest request) {
        Optional<User> maybeUser = userRepository.findByUsername(request.identifier())
                .or(() -> userRepository.findByEmail(request.identifier()));

        String resolvedUsername = maybeUser.map(User::getUsername).orElse(request.identifier());

        // Block unverified accounts before checking the password at all — consistent
        // with how suspended/locked accounts are already rejected ahead of credential
        // verification, via Spring Security's own pre-authentication check ordering.
        if (maybeUser.isPresent() && !maybeUser.get().isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email address before logging in");
        }

        CustomUserDetails userDetails;
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(resolvedUsername, request.password())
            );
            userDetails = (CustomUserDetails) authentication.getPrincipal();
        } catch (DisabledException | LockedException e) {
            throw new AccountSuspendedException("This account has been suspended or is temporarily locked");
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailedAttempt(resolvedUsername);
            throw new InvalidCredentialsException("Invalid username or password");
        }

        User user = userDetails.getUser();
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        return tokenIssuer.issueNewSession(user);
    }

    @Transactional
    public TokenIssuer.IssuedTokens refresh(String rawRefreshToken) {
        String hash = tokenHasher.hash(rawRefreshToken);

        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidCredentialsException("Refresh token has expired");
        }

        //atomic compare-and-swap: only one concurrent request can win this/
        int rowsUpdated = refreshTokenRepository.revokeIfActive(tokenEntity.getId());

        if (rowsUpdated == 0) {
            //someone already consumed this exact token. Either a genuine race
            // (rare) or a stolen token being replayed - treat both as compromise
            // and kill the whole lineage, forcing a fresh login.
            refreshTokenRepository.revokeFamily(tokenEntity.getFamilyId());
            throw new RefreshTokenReuseException("This session has been comprimised or reused. Please log in again");
        }

        User user = tokenEntity.getUser();
        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountSuspendedException("This account has been suspended");
        }

        return tokenIssuer.issueTokenPair(user, tokenEntity.getFamilyId());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = tokenHasher.hash(rawRefreshToken);

        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }
}