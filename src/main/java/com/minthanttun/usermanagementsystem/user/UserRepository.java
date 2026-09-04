package com.minthanttun.usermanagementsystem.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByPendingEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    long countByRoleAndStatus(Role role, AccountStatus status);
}