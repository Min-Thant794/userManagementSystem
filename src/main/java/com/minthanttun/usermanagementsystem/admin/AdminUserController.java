package com.minthanttun.usermanagementsystem.admin;

import com.minthanttun.usermanagementsystem.admin.dto.*;
import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.user.AccountStatus;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(
            summary = "List users",
            description = "Retrieves a paginated list of users. Administrators can optionally filter users by search text, role and account status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Administrator privileges are required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
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

    @Operation(
            summary = "Get user by ID",
            description = "Retrieves detailed information about a specific user. This endpoint is restricted to administrators."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Administrator privileges are required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse getUsers(@PathVariable UUID id) {
        return adminUserService.getCachedUserResponse(id);
    }

    @Operation(
            summary = "Update user",
            description = "Updates an existing user's account information. This endpoint is restricted to administrators."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Administrator privileges are required."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
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

    @Operation(
            summary = "Update user account status",
            description = "Changes the account status of an existing user. This endpoint is restricted to administrators."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User status successfully updated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account status"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Administrator privileges are required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            ),
    })
    @SecurityRequirement(name = "bearerAuth")
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

    @Operation(
            summary = "Update user role",
            description = "Changes the role assigned to an existing user. This endpoint is restricted to administrators."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User role successfully updated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid role"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Administrator privileges are required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
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

    @Operation(
            summary = "Create an administrator",
            description = "Creates a new administrator account. Only an authentication administrator can perform this operation."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Administrator successfully created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid administrator data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Administrator privileges are required"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Username or email already exists"
            ),
    })
    @SecurityRequirement(name = "bearerAuth")
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