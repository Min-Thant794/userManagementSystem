package com.minthanttun.usermanagementsystem.user;

import com.minthanttun.usermanagementsystem.common.exception.DuplicateResourceException;
import com.minthanttun.usermanagementsystem.common.exception.InvalidCredentialsException;
import com.minthanttun.usermanagementsystem.common.exception.ProfileIncompleteException;
import com.minthanttun.usermanagementsystem.user.dto.ChangePasswordRequest;
import com.minthanttun.usermanagementsystem.user.dto.CompleteProfileRequest;
import com.minthanttun.usermanagementsystem.user.dto.SetInitialPasswordRequest;
import com.minthanttun.usermanagementsystem.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileImageService profileImageService;

    @Transactional
    public User updateProfile(User currentUser, UpdateProfileRequest request) {

        if (request.username() != null && !request.username().equals(currentUser.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new DuplicateResourceException("Username is already taken");
            }
            currentUser.setUsername(request.username());
        }

        if (request.email() != null && !request.email().equals(currentUser.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new DuplicateResourceException("Email is already registered");
            }
            currentUser.setEmail(request.email());
        }

        if (request.phoneNumber() != null && !request.phoneNumber().equals(currentUser.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
                throw new DuplicateResourceException("Phone number is already registered");
            }
            currentUser.setPhoneNumber(request.phoneNumber());
        }

        return userRepository.save(currentUser);
    }

    @Transactional
    public User uploadProfilePhoto(User user, MultipartFile file) {
        String imageUrl = profileImageService.uploadImage(file, user.getId());
        user.setProfileImageUrl(imageUrl);
        return userRepository.save(user);
    }

    @Transactional
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
}
