package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.auth.dto.*;
import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.security.jwt.CookieUtil;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final CookieUtil cookieUtil;
    private final EmailVerificationService emailVerificationService;
    private final SessionService sessionService;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        User created = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        var tokens = authService.login(request, httpRequest);
        cookieUtil.setRefreshTokenCookie(response, tokens.refreshToken(), tokens.refreshTokenExpiryMs());
        return ResponseEntity.ok(AuthResponse.of(tokens.accessToken(), tokens.refreshTokenExpiryMs()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refreshToken") String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        var tokens = authService.refresh(refreshToken, httpRequest);
        cookieUtil.setRefreshTokenCookie(response, tokens.refreshToken(), tokens.refreshTokenExpiryMs());
        return ResponseEntity.ok(AuthResponse.of(tokens.accessToken(), tokens.refreshTokenExpiryMs()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        cookieUtil.clearRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public List<SessionResponse> listSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        return sessionService.listSessions(userDetails.getUser().getId(), refreshToken);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId
    ) {
        sessionService.revokeSession(sessionId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/others")
    public ResponseEntity<Void> revokeOtherSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @CookieValue(name = "refreshToken", required = false)
            String refreshToken
    ) {
        sessionService.revokeAllOtherSessions(userDetails.getUser().getId(), refreshToken);
        return ResponseEntity.noContent().build();
    }
}