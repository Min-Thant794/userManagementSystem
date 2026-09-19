package com.minthanttun.usermanagementsystem.user;

import com.minthanttun.usermanagementsystem.auth.EmailVerificationService;
import com.minthanttun.usermanagementsystem.common.exception.DuplicateResourceException;
import com.minthanttun.usermanagementsystem.common.exception.InvalidCredentialsException;
import com.minthanttun.usermanagementsystem.common.exception.ProfileIncompleteException;
import com.minthanttun.usermanagementsystem.common.exception.ResourceNotFoundException;
import com.minthanttun.usermanagementsystem.security.jwt.SessionRevocationService;
import com.minthanttun.usermanagementsystem.user.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ProfileImageService profileImageService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private SessionRevocationService sessionRevocationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder,
                profileImageService,
                emailVerificationService,
                sessionRevocationService
        );
    }

    @Test
    void updateProfile_shouldUpdateUserSuccessfully() {
        //Arrange
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("olduser")
                .email("old@example.com")
                .phoneNumber("+6591111111")
                .build();

        UpdateProfileRequest request = mock(UpdateProfileRequest.class);

        when(request.username()).thenReturn("newuser");

        when(request.email()).thenReturn("new@example.com");

        when(request.phoneNumber()).thenReturn("+659222222");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        when(userRepository.existsByPendingEmail("new@example.com")).thenReturn(false);

        when(userRepository.existsByPhoneNumber("+659222222")).thenReturn(false);

        when(userRepository.save(user)).thenReturn(user);

        //Act
        User result = userService.updateProfile(user, request);

        //Assert
        assertThat(result).isSameAs(user);

        assertThat(user.getUsername()).isEqualTo("newuser");

        assertThat(user.getEmail()).isEqualTo("old@example.com");

        assertThat(user.getPendingEmail()).isEqualTo("new@example.com");

        assertThat(user.getPhoneNumber()).isEqualTo("+659222222");

        verify(userRepository).save(user);

        verify(emailVerificationService).generateVerificationEmail(
                user,
                "new@example.com"
        );
    }

    @Test
    void updateProfile_shouldRejectDuplicateUsername() {
        //Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("olduser")
                .email("old@example.com")
                .phoneNumber("+6591111111")
                .build();

        UpdateProfileRequest request = new UpdateProfileRequest(
                "existinguser",
                null,
                null
        );

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        //Act & Assert
        assertThatThrownBy(() -> userService.updateProfile(user, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username is already taken");

        //Nothing should be saved
        verify(userRepository).existsByUsername("existinguser");

        verify(userRepository, never()).existsByEmail(anyString());

        verify(userRepository, never()).existsByPendingEmail(anyString());

        verify(userRepository, never()).existsByPhoneNumber(anyString());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateProfile_shouldRejectDuplicateEmail() {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("old@example.com")
                .phoneNumber("+6591111111")
                .build();

        UpdateProfileRequest request = new UpdateProfileRequest(
                null,
                "existing@example.com",
                null
        );

        when(userRepository.existsByEmail("existing@example.com"))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() ->
                userService.updateProfile(user, request)
        )
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email is already registered");

        verify(userRepository).existsByEmail("existing@example.com");
        verify(userRepository, never()).existsByPendingEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(emailVerificationService, never())
                .generateVerificationEmail(any(User.class), anyString());
    }

    @Test
    void updateProfile_shouldRejectDuplicatePhoneNumber() {
        //Arrange
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .phoneNumber("+6591111111")
                .build();

        UpdateProfileRequest request = mock(UpdateProfileRequest.class);

        when(request.username()).thenReturn(null);

        when(request.email()).thenReturn(null);

        when(request.phoneNumber()).thenReturn("+6592222222");

        when(userRepository.existsByPhoneNumber("+6592222222")).thenReturn(true);

        //Act & Assert
        assertThatThrownBy(() -> userService.updateProfile(user, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Phone number is already registered");

        verify(userRepository, never()).save(any(User.class));

        assertThat(user.getPhoneNumber()).isEqualTo("+6591111111");
    }

    @Test
    void updateProfile_shouldRejectDuplicatePendingEmail() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("old@example.com")
                .phoneNumber("+6591111111")
                .build();

        UpdateProfileRequest request = new UpdateProfileRequest(
                null,
                "pending@example.com",
                null
        );

        when(userRepository.existsByEmail("pending@example.com")).thenReturn(false);
        when(userRepository.existsByPendingEmail("pending@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(user, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email is already registered");

        verify(userRepository).existsByEmail("pending@example.com");
        verify(userRepository).existsByPendingEmail("pending@example.com");
        verify(userRepository, never()).save(any(User.class));
        verify(emailVerificationService, never())
                .generateVerificationEmail(any(User.class), anyString());
    }

    @Test
    void updateProfile_shouldNotChangeUnspecifiedOrUnchangedFields() {
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .phoneNumber("+6591111111")
                .build();

        UpdateProfileRequest request = mock(UpdateProfileRequest.class);

        when(request.username()).thenReturn("testuser");

        when(request.email()).thenReturn(null);

        when(request.phoneNumber()).thenReturn("+6591111111");

        when(userRepository.save(user)).thenReturn(user);

        User result = userService.updateProfile(user, request);

        assertThat(result).isSameAs(user);

        assertThat(user.getUsername()).isEqualTo("testuser");

        assertThat(user.getEmail()).isEqualTo("test@example.com");

        assertThat(user.getPhoneNumber()).isEqualTo("+6591111111");

        assertThat(user.getPendingEmail()).isNull();

        verify(userRepository).save(user);

        verify(userRepository, never()).existsByUsername(anyString());

        verify(userRepository, never()).existsByEmail(anyString());

        verify(userRepository, never()).existsByPendingEmail(anyString());

        verify(userRepository, never()).existsByPhoneNumber(anyString());

        verify(emailVerificationService, never()).generateVerificationEmail(any(User.class), anyString());
    }

    @Test
    void uploadProfilePhoto_shouldUploadAndSaveUrl() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        MultipartFile file = mock(MultipartFile.class);

        String imageUrl = "https://example.com/profile-image.jpg";

        when(profileImageService.uploadImage(file, userId)).thenReturn(imageUrl);

        when(userRepository.save(user)).thenReturn(user);

        User result = userService.uploadProfilePhoto(user, file);

        assertThat(result).isSameAs(user);

        assertThat(user.getProfileImageUrl()).isEqualTo(imageUrl);

        verify(profileImageService).uploadImage(file, userId);

        verify(userRepository).save(user);
    }

    @Test
    void deleteProfilePhoto_shouldDeleteExistingPhoto() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .profileImageUrl("https://example.com/profile-image.jpg")
                .build();

        when(userRepository.save(user)).thenReturn(user);

        User result = userService.deleteProfilePhoto(user);

        assertThat(result).isSameAs(user);

        assertThat(user.getProfileImageUrl()).isNull();

        verify(profileImageService).deleteImage(userId);
        verify(userRepository).save(user);
    }

    @Test
    void deleteProfilePhoto_shouldDoNothingWhenNoPhotoExists() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .profileImageUrl(null)
                .build();

        User result = userService.deleteProfilePhoto(user);

        assertThat(result).isSameAs(user);

        assertThat(user.getProfileImageUrl()).isNull();

        verify(profileImageService, never()).deleteImage(any(UUID.class));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_shouldRejectWhenNoPasswordExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPassword",
                "newPassword"
        );

        assertThatThrownBy(() -> userService.changePassword(user, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("This account has no password set yet. Use the set-password endpoint instead.");

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(sessionRevocationService);
    }

    @Test
    void changePassword_shouldRejectIncorrectCurrentPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("existing-hash")
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "wrongPassword",
                "newPassword"
        );

        when(passwordEncoder.matches("wrongPassword", "existing-hash"))
                .thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(user, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Current password is incorrect");

        verify(passwordEncoder).matches("wrongPassword", "existing-hash");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(sessionRevocationService);
    }

    @Test
    void changePassword_shouldUpdatePasswordAndRevokeSessions() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .passwordHash("old-hash")
                .build();

        ChangePasswordRequest request = mock(ChangePasswordRequest.class);

        when(request.currentPassword()).thenReturn("oldPassword");

        when(request.newPassword()).thenReturn("newPassword");

        when(passwordEncoder.matches("oldPassword", "old-hash"))
                .thenReturn(true);

        when(passwordEncoder.encode("newPassword")).thenReturn("new-hash");

        when(userRepository.save(user)).thenReturn(user);

        userService.changePassword(user, request);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");

        verify(passwordEncoder).matches("oldPassword", "old-hash");

        verify(passwordEncoder).encode("newPassword");

        verify(userRepository).save(user);

        verify(sessionRevocationService).revokeAllSessions(userId);
    }

    @Test
    void setInitialPassword_shouldRejectWhenPasswordAlreadyExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("existing-hash")
                .build();

        SetInitialPasswordRequest request =
                new SetInitialPasswordRequest("newPassword");

        assertThatThrownBy(() -> userService.setInitialPassword(user, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("This account already has a password. Use the change-password endpoint instead.");

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void setInitialPassword_shouldSetPasswordSuccessfully() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .passwordHash(null)
                .build();

        SetInitialPasswordRequest request = mock(SetInitialPasswordRequest.class);

        when(request.newPassword()).thenReturn("newPassword");

        when(passwordEncoder.encode("newPassword")).thenReturn("encoded-new-password");

        when(userRepository.save(user)).thenReturn(user);

        userService.setInitialPassword(user, request);

        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-password");

        verify(passwordEncoder).encode("newPassword");

        verify(userRepository).save(user);
    }

    @Test
    void completeProfile_shouldRejectWhenProfileIsAlreadyComplete() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("existinguser")
                .email("existing@example.com")
                .phoneNumber("+6591111111")
                .build();

        CompleteProfileRequest request = new CompleteProfileRequest(
                "newuser",
                "+6592222222"
        );

        assertThatThrownBy(() -> userService.completeProfile(user, request))
                .isInstanceOf(ProfileIncompleteException.class)
                .hasMessage("Profile is already complete");

        verifyNoInteractions(userRepository);
    }

    @Test
    void completeProfile_shouldRejectDuplicateUsername() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();

        CompleteProfileRequest request = new CompleteProfileRequest(
                "existinguser",
                "+6592222222"
        );

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThatThrownBy(() -> userService.completeProfile(user, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username is already taken");

        verify(userRepository).existsByUsername("existinguser");
        verify(userRepository, never()).existsByPhoneNumber(anyString());
        verify(userRepository, never()).save(any(User.class));

        assertThat(user.getUsername()).isNull();
        assertThat(user.getPhoneNumber()).isNull();
    }

    @Test
    void completeProfile_shouldRejectDuplicatePhoneNumber() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();

        CompleteProfileRequest request = new CompleteProfileRequest(
                "newuser",
                "+6592222222"
        );

        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        when(userRepository.existsByPhoneNumber("+6592222222")).thenReturn(true);

        assertThatThrownBy(() -> userService.completeProfile(user, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Phone number is already registered");

        verify(userRepository).existsByUsername("newuser");

        verify(userRepository).existsByPhoneNumber("+6592222222");

        verify(userRepository, never()).save(any(User.class));

        assertThat(user.getUsername()).isNull();

        assertThat(user.getPhoneNumber()).isNull();
    }

    @Test
    void completeProfile_shouldCompleteProfileSuccessfully() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();

        CompleteProfileRequest request = new CompleteProfileRequest(
                "newuser",
                "+6592222222"
        );

        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        when(userRepository.existsByPhoneNumber("+6592222222")).thenReturn(false);

        when(userRepository.save(user)).thenReturn(user);

        User result = userService.completeProfile(user, request);

        assertThat(result).isSameAs(user);

        assertThat(user.getUsername()).isEqualTo("newuser");

        assertThat(user.getPhoneNumber()).isEqualTo("+6592222222");

        verify(userRepository).existsByUsername("newuser");

        verify(userRepository).existsByPhoneNumber("+6592222222");

        verify(userRepository).save(user);
    }

    @Test
    void getCachedProfile_shouldReturnUserResponse() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .pendingEmail("new@example.com")
                .phoneNumber("+6591234567")
                .role(Role.USER)
                .profileImageUrl("https://example.com/avatar.jpg")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse result = userService.getCachedProfile(userId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo("testuser");
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.pendingEmail()).isEqualTo("new@example.com");
        assertThat(result.phoneNumber()).isEqualTo("+6591234567");
        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(result.profileImageUrl())
                .isEqualTo("https://example.com/avatar.jpg");

        verify(userRepository).findById(userId);
    }

    @Test
    void getCachedProfile_shouldThrowWhenUserNotFound() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCachedProfile(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found: " + userId);

        verify(userRepository).findById(userId);
    }
}
