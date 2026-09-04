package com.minthanttun.usermanagementsystem.admin;

import com.minthanttun.usermanagementsystem.admin.dto.AdminUpdateUserRequest;
import com.minthanttun.usermanagementsystem.admin.dto.AdminUserResponse;
import com.minthanttun.usermanagementsystem.admin.dto.CreateAdminRequest;
import com.minthanttun.usermanagementsystem.admin.dto.UserSearchCriteria;
import com.minthanttun.usermanagementsystem.audit.AuditAction;
import com.minthanttun.usermanagementsystem.audit.AuditService;
import com.minthanttun.usermanagementsystem.common.exception.DuplicateResourceException;
import com.minthanttun.usermanagementsystem.common.exception.LastAdminException;
import com.minthanttun.usermanagementsystem.common.exception.ResourceNotFoundException;
import com.minthanttun.usermanagementsystem.user.AccountStatus;
import com.minthanttun.usermanagementsystem.user.Role;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public Page<User> listUsers(UserSearchCriteria criteria, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.matchesSearchTerm(criteria.search()),
                UserSpecifications.hasRole(criteria.role()),
                UserSpecifications.hasStatus(criteria.status())
        );

        return userRepository.findAll(spec, pageable);
    }

    // Internal use only - for mutation methods that need a live, transaction entity.
    public User getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    // Used only by the GET /{id} read endpoint - cached, DTO-returning.
    @Cacheable(value = "adminUsers", key = "#id")
    public AdminUserResponse getCachedUserResponse(UUID id) {
        return AdminUserResponse.from(getUser(id));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "adminUsers", key = "#id"),
            @CacheEvict(value = "users", key = "#id")
    })
    public User updateUser(UUID id, AdminUpdateUserRequest request, User actor) {
        User user = getUser(id);

        String beforeUsername = user.getUsername();
        String beforeEmail = user.getEmail();
        String beforePhoneNumber = user.getPhoneNumber();

        if(request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new DuplicateResourceException("Username is already taken");
            }
            user.setUsername(request.username());
        }

        if(request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new DuplicateResourceException("Email is already registered");
            }
            user.setEmail(request.email());
        }

        if (request.phoneNumber() != null && !request.phoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
                throw new DuplicateResourceException("Phone number is already registered");
            }
            user.setPhoneNumber(request.phoneNumber());
        }

        User saved = userRepository.save(user);

        boolean actuallyChanged = !Objects.equals(beforeUsername, saved.getUsername())
                || !Objects.equals(beforeEmail, saved.getEmail())
                || !Objects.equals(beforePhoneNumber, saved.getPhoneNumber());

        if (actuallyChanged) {
            auditService.log(
                    actor.getId(),
                    saved.getId(),
                    AuditAction.UPDATE,
                    Map.of(
                            "before", nullSafeMap(
                                    "username", beforeUsername,
                                    "email", beforeEmail,
                                    "phoneNumber", beforePhoneNumber
                            ),
                            "after", nullSafeMap(
                                    "username", saved.getUsername(),
                                    "email", saved.getEmail(),
                                    "phoneNumber", saved.getPhoneNumber()
                            )
                    )
            );
        }

        return saved;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "adminUsers", key = "#id"),
            @CacheEvict(value = "users", key = "#id")
    })
    public User updateStatus(UUID id, AccountStatus newStatus, User actor) {
        User target = getUser(id);

        if (target.getStatus() == newStatus) {
            return target; //nothing to do
        }

        boolean isDemotingActiveAdmin = target.getRole() == Role.ADMIN && target.getStatus() == AccountStatus.ACTIVE && newStatus == AccountStatus.SUSPENDED;

        if (isDemotingActiveAdmin) {
            long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new LastAdminException("Cannot suspend the last remaining admin");
            }
        }

        AccountStatus previousStatus = target.getStatus();
        target.setStatus(newStatus);
        User saved = userRepository.save(target);

        auditService.log(
                actor.getId(),
                target.getId(),
                newStatus == AccountStatus.SUSPENDED ? AuditAction.SUSPEND : AuditAction.REACTIVATE,
                Map.of("before", previousStatus.name(), "after", newStatus.name())
        );

        return saved;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "adminUsers", key = "#id"),
            @CacheEvict(value = "users", key = "#id")
    })
    public User updateRole(UUID id, Role newRole, User actor) {
        User target = getUser(id);

        if (target.getRole() == newRole) {
            return target; //nothing to do
        }

        boolean isDemotingActiveAdmin = target.getRole() == Role.ADMIN && target.getStatus() == AccountStatus.ACTIVE && newRole == Role.USER;

        if (isDemotingActiveAdmin) {
            long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new LastAdminException("Cannot demote the last remaining active admin");
            }
        }

        Role previousRole = target.getRole();
        target.setRole(newRole);
        User saved = userRepository.save(target);

        auditService.log(
                actor.getId(),
                target.getId(),
                AuditAction.ROLE_CHANGE,
                Map.of("before", previousRole.name(), "after", newRole.name())
        );

        return saved;
    }

    @Transactional
    public User createAdmin(CreateAdminRequest request, User actor) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new DuplicateResourceException("Phone number is already registered");
        }

        User newAdmin = User.builder()
                .username(request.username())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .build();

        User saved = userRepository.save(newAdmin);

        auditService.log(
                actor.getId(),
                saved.getId(),
                AuditAction.ADMIN_CREATED,
                Map.of("username", saved.getUsername(), "email", saved.getEmail())
        );

        return saved;
    }

    private Map<String, Object> nullSafeMap(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, Object> map = new HashMap<>();
        map.put(k1, v1 == null ? "" : v1);
        map.put(k2, v2 == null ? "" : v2);
        map.put(k3, v3 == null ? "" : v3);
        return map;
    }
}
