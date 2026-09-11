package com.minthanttun.usermanagementsystem.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "User successfully registered",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid registration data"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Username or email already exists"
            )
    })
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        User created = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    @Operation(
            summary = "Verify email address",
            description = "Verifies a user's email address using the verification token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Email successfully verified"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or expired verification token"
            )
    })
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Resend email verification",
            description = "Sends a new email verification message to the specified email address."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Verification email request processed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid email address"
            )
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Authentication user",
            description = "Authentications a user and returns an access token. A refresh token is issued as an HTTP cookie."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentication successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid login request"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid username or password"
            ),
            @ApiResponse(
                    responseCode = "423",
                    description = "Account is locked"
            )
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        var tokens = authService.login(request, httpRequest);
        cookieUtil.setRefreshTokenCookie(response, tokens.refreshToken(), tokens.refreshTokenExpiryMs());
        return ResponseEntity.ok(AuthResponse.of(tokens.accessToken(), tokens.refreshTokenExpiryMs()));
    }

    @Operation(
            summary = "Refresh access token",
            description = "Issues a new access token using the refresh token stored in the refreshToken HTTP cookie."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Access token successfully refreshed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired refresh token"
            )
    })
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

    @Operation(
            summary = "Log out",
            description = "Revokes the current refresh token, if present, and clears the refresh token cookie."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Logout completed successfully"
            )
    })
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

    @Operation(
            summary = "Request password reset",
            description = "Initiates the password reset process for the supplied email address."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Password reset request processed"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Reset password",
            description = "Sets a new password using a valid password reset token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Password successfully reset"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or expired reset token"
            )
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "List active sessions",
            description = "Returns the active sessions belonging to the currently authenticated user." +
                    "The current session is identified using the refreshToken cookie when available."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Sessions retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(implementation = SessionResponse.class)
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/sessions")
    public List<SessionResponse> listSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        return sessionService.listSessions(userDetails.getUser().getId(), refreshToken);
    }

    @Operation(
            summary = "Revoke a session",
            description = "Revokes a specific session belonging to the currently authenticated user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Session successfully revoked"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Session not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId
    ) {
        sessionService.revokeSession(sessionId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Revoke other sessions",
            description = "Revokes all sessions belonging to the authenticated user except the current session."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Other sessions successfully revoked"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
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