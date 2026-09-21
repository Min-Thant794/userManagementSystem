package com.minthanttun.usermanagementsystem.user;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("regression")
class ProfileImageServiceRegressionTest {
    @ParameterizedTest @ValueSource(strings = {"gif", "bmp"})
    void otherDecodableFormatsAreRejectedBeforeCloudinary(String format) throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        var service = new ProfileImageService(cloudinary);
        // Intentionally misleading name/type: validate actual bytes, not client metadata.
        var file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                ProfileImageServiceTest.imageBytes(format, 50, 50));
        assertThatThrownBy(() -> service.uploadImage(file, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPEG, PNG, or WebP");
        verifyNoInteractions(cloudinary);
    }
}