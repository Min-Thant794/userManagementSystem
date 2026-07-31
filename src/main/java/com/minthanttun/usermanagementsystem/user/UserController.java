package com.minthanttun.usermanagementsystem.user;

import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.user.dto.ChangePasswordRequest;
import com.minthanttun.usermanagementsystem.user.dto.SetInitialPasswordRequest;
import com.minthanttun.usermanagementsystem.user.dto.UpdateProfileRequest;
import com.minthanttun.usermanagementsystem.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return UserResponse.from(userDetails.getUser());
    }

    @PatchMapping("/me")
    public UserResponse updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        User updated = userService.updateProfile(userDetails.getUser(), request);
        return UserResponse.from(updated);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(userDetails.getUser(), request);
        return ResponseEntity.noContent().build();
    }

    //singup with oauth, dont have local password
    @PutMapping("/me/password/initial")
    public ResponseEntity<Void> setInitialPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SetInitialPasswordRequest request
    ) {
        userService.setInitialPassword(userDetails.getUser(), request);
        return ResponseEntity.noContent().build();
    }
}
