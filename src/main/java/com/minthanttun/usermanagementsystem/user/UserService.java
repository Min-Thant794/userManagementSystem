package com.minthanttun.usermanagementsystem.user;

import com.minthanttun.usermanagementsystem.auth.EmailVerificationService;
import com.minthanttun.usermanagementsystem.common.exception.DuplicateResourceException;
import com.minthanttun.usermanagementsystem.common.exception.InvalidCredentialsException;
import com.minthanttun.usermanagementsystem.common.exception.ProfileIncompleteException;
import com.minthanttun.usermanagementsystem.common.exception.ResourceNotFoundException;
import com.minthanttun.usermanagementsystem.user.dto.ChangePasswordRequest;
import com.minthanttun.usermanagementsystem.user.dto.CompleteProfileRequest;
import com.minthanttun.usermanagementsystem.user.dto.SetInitialPasswordRequest;
import com.minthanttun.usermanagementsystem.user.dto.UpdateProfileRequest;
import com.minthanttun.usermanagementsystem.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileImageService profileImageService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#currentUser.id"),
            @CacheEvict(value = "adminUsers", key = "#currentUser.id")
    })
    public User updateProfile(User user, UpdateProfileRequest request) {

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new DuplicateResourceException("Username is already taken");
            }
            user.setUsername(request.username());
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email()) || userRepository.existsByPendingEmail(request.email())) {
                throw new DuplicateResourceException("Email is already registered");
            }
            user.setPendingEmail(request.email());
            emailVerificationService.generateVerificationEmail(user, request.email());
        }

        if (request.phoneNumber() != null && !request.phoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
                throw new DuplicateResourceException("Phone number is already registered");
            }
            user.setPhoneNumber(request.phoneNumber());
        }

        return userRepository.save(user);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#user.id"),
            @CacheEvict(value = "adminUsers", key = "#user.id")
    })
    public User uploadProfilePhoto(User user, MultipartFile file) {
        String imageUrl = profileImageService.uploadImage(file, user.getId());
        user.setProfileImageUrl(imageUrl);
        return userRepository.save(user);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#user.id"),
            @CacheEvict(value = "adminUsers", key = "#user.id")
    })
    public User deleteProfilePhoto(User user) {
        if (user.getProfileImageUrl() != null) {
            profileImageService.deleteImage(user.getId());
            user.setProfileImageUrl(null);
            return userRepository.save(user);
        }
        return user;
    }

    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequest request) {
        if (currentUser.getPasswordHash() == null) {
            throw new InvalidCredentialsException("This account has no password set yet. Use the set-password endpoint instead.");
        }

        if (!passwordEncoder.matches(request.currentPassword(), currentUser.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        currentUser.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(currentUser);
    }

    @Transactional
    public void setInitialPassword(User currentUser, SetInitialPasswordRequest request) {
        if (currentUser.getPasswordHash() != null) {
            throw new InvalidCredentialsException("This account already has a password. Use the change-password endpoint instead.");
        }

        currentUser.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(currentUser);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#user.id"),
            @CacheEvict(value = "adminUsers", key = "#user.id")
    })
    public User completeProfile(User user, CompleteProfileRequest request) {
        if (user.isProfileComplete()) {
            throw new ProfileIncompleteException("Profile is already complete");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }

        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new DuplicateResourceException("Phone number is already registered");
        }

        user.setUsername(request.username());
        user.setPhoneNumber(request.phoneNumber());
        return userRepository.save(user);
    }

    @Cacheable(value = "users", key = "#userId")
    public UserResponse getCachedProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return UserResponse.from(user);
    }
}
