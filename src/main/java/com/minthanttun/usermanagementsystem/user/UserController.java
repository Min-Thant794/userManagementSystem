package com.minthanttun.usermanagementsystem.user;

import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.user.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ProfileImageService profileImageService;

    @Operation(
            summary = "Get current user",
            description = "Retrieves the profile of the currently authenticated user."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Current user retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return userService.getCachedProfile(userDetails.getUser().getId());
    }

    @Operation(
            summary = "Update current user profile",
            description = "Updates editable profile information for the currently authenticated user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile successfully updated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid profile data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/me")
    public UserResponse updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        User updated = userService.updateProfile(userDetails.getUser(), request);
        return UserResponse.from(updated);
    }

    @Operation(
            summary = "Change password",
            description = "Changes the password of the currently authenticated user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Password successfully changed"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid password request"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required or the current password is incorrect"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(userDetails.getUser(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Set initial password",
            description = "Sets an initial local password for an authenticated user who does not currently have a local password, such as a user who registered through OAuth."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Initial password successfully set"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid password request or user already has a password"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    // Users who signed up through OAuth may not have a local password.
    @PutMapping("/me/password/initial")
    public ResponseEntity<Void> setInitialPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SetInitialPasswordRequest request
    ) {
        userService.setInitialPassword(userDetails.getUser(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Upload profile photo",
            description = "Uploads or replaces the profile photo of the currently authenticated user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile photo successfully uploaded",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or unsupported image file"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me/photo")
    public UserResponse uploadProfilePhoto(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file
    ) {
        User updated = userService.uploadProfilePhoto(userDetails.getUser(), file);
        return UserResponse.from(updated);
    }

    @Operation(
            summary = "Delete profile photo",
            description = "Removes the profile photo of the currently authenticated user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile photo successfully deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me/photo")
    public UserResponse deleteProfilePhoto(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User updated = userService.deleteProfilePhoto(userDetails.getUser());
        return UserResponse.from(updated);
    }

    @Operation(
            summary = "Complete user profile",
            description = "Completes or updates the required profile information for the currently authenticated user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile successfully completed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid profile data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/me/complete-profile")
    public UserResponse completeProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CompleteProfileRequest request
    ) {
        User updated = userService.completeProfile(userDetails.getUser(), request);
        return UserResponse.from(updated);
    }
}
