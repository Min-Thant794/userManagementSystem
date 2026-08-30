package com.minthanttun.usermanagementsystem.admin;

import com.minthanttun.usermanagementsystem.admin.dto.*;
import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.user.AccountStatus;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.Role;
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
    public Page<AdminUserResponse> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            Pageable pageable
    ) {
        UserSearchCriteria criteria = new UserSearchCriteria(search, role, status);
        return adminUserService.listUsers(criteria, pageable).map(AdminUserResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse getUsers(@PathVariable UUID id) {
        return AdminUserResponse.from(adminUserService.getUser(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateUserRequest request,
            @AuthenticationPrincipal CustomUserDetails actor
    ) {
        User updated = adminUserService.updateUser(id, request, actor.getUser());
        return AdminUserResponse.from(updated);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails actor
    ) {
        User updated = adminUserService.updateStatus(id, request.status(), actor.getUser());
        return AdminUserResponse.from(updated);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal CustomUserDetails actor
    ) {
        User updated = adminUserService.updateRole(id, request.role(), actor.getUser());
        return AdminUserResponse.from(updated);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserResponse> createAdmin(
            @Valid @RequestBody CreateAdminRequest request,
            @AuthenticationPrincipal CustomUserDetails actor
    ) {
        User created = adminUserService.createAdmin(request, actor.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminUserResponse.from(created));
    }
}