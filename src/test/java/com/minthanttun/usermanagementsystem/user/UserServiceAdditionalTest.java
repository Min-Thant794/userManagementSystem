package com.minthanttun.usermanagementsystem.user;

import org.hibernate.sql.Update;
import org.junit.jupiter.api.Tag;
import com.minthanttun.usermanagementsystem.auth.EmailVerificationService;
import com.minthanttun.usermanagementsystem.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("remaining-unit")
public class UserServiceAdditionalTest {
    @Mock UserRepository userRepository;
    @Mock ProfileImageService profileImageService;
    @Mock EmailVerificationService emailVerificationService;
    @InjectMocks UserService userService;

    @Test
    void unchangedCurrentEmailDoesNotCheckDuplicatesOrSendVerification() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .pendingEmail("pending@example.com")
                .build();

        when(userRepository.save(user)).thenReturn(user);
        assertThat(userService.updateProfile(user, new UpdateProfileRequest(null, "test@example.com", null))).isSameAs(user);
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getPendingEmail()).isEqualTo("pending@example.com");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).existsByPendingEmail(anyString());
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void uploadFailurePreservesExistingUrlAndDoesNotSave() {
        User user = User.builder().id(UUID.randomUUID()).profileImageUrl("https://example.com/old.jpg").build();
        MultipartFile file = mock(MultipartFile.class);
        var failure = new IllegalArgumentException("invalid image");
        when(profileImageService.uploadImage(file, user.getId())).thenThrow(failure);
        assertThatThrownBy(() -> userService.uploadProfilePhoto(user, file)).isSameAs(failure);
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/old.jpg");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deletionFailurePreservesExistingUrlAndDoesNotSave() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .profileImageUrl("https://example.com/old.jpg")
                .build();
        var failure = new RuntimeException("remote deletion failed");
        doThrow(failure).when(profileImageService).deleteImage(user.getId());
        assertThatThrownBy(() -> userService.deleteProfilePhoto(user)).isSameAs(failure);
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/old.jpg");
        verify(userRepository, never()).save(any());
    }
}
