package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.auth.dto.LoginRequest;
import com.minthanttun.usermanagementsystem.auth.dto.SignupRequest;
import com.minthanttun.usermanagementsystem.common.exception.AccountSuspendedException;
import com.minthanttun.usermanagementsystem.common.exception.DuplicateResourceException;
import com.minthanttun.usermanagementsystem.common.exception.InvalidCredentialsException;
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

        return userRepository.save(user);
    }

    @Transactional
    public TokenIssuer.IssuedTokens login(LoginRequest request) {
        String resolvedUsername = userRepository.findByUsername(request.identifier())
                .or(() -> userRepository.findByEmail(request.identifier()))
                .map(User::getUsername)
                .orElse(request.identifier());

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

        return tokenIssuer.issueTokenPair(user);
    }

    @Transactional
    public TokenIssuer.IssuedTokens refresh(String rawRefreshToken) {
        String hash = tokenHasher.hash(rawRefreshToken);

        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (tokenEntity.isRevoked()) {
            throw new InvalidCredentialsException("Refresh token has been revoked");
        }

        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidCredentialsException("Refresh token has expired");
        }

        User user = tokenEntity.getUser();
        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountSuspendedException("This account has been suspended");
        }

        // Rotate: revoke the old refresh token, issue a brand new pair.
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        return tokenIssuer.issueTokenPair(user);
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