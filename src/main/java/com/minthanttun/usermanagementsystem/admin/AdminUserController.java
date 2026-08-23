package com.minthanttun.usermanagementsystem.admin;

import com.minthanttun.usermanagementsystem.admin.dto.AdminUpdateUserRequest;
import com.minthanttun.usermanagementsystem.admin.dto.ChangeRoleRequest;
import com.minthanttun.usermanagementsystem.admin.dto.ChangeStatusRequest;
import com.minthanttun.usermanagementsystem.admin.dto.CreateAdminRequest;
import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> listUsers(Pageable pageable) {
        return adminUserService.listUsers(pageable).map(UserResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUsers(@PathVariable UUID id) {
        return UserResponse.from(adminUserService.getUser(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(@PathVariable UUID id, @Valid @RequestBody AdminUpdateUserRequest request) {
        User updated = adminUserService.updateUser(id, request);
        return UserResponse.from(updated);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails actor
            ) {
        User updated = adminUserService.updateStatus(id, request.status(), actor.getUser());
        return UserResponse.from(updated);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal CustomUserDetails actor
            ) {
        User updated = adminUserService.updateRole(id, request.role(), actor.getUser());
        return UserResponse.from(updated);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createAdmin(
            @Valid @RequestBody CreateAdminRequest request,
            @AuthenticationPrincipal CustomUserDetails actor
            ) {
        User created = adminUserService.createAdmin(request, actor.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }
}
