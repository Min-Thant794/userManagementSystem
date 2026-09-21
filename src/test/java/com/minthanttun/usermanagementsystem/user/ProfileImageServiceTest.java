package com.minthanttun.usermanagementsystem.user;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {
    @Mock Cloudinary cloudinary;
    @Mock Uploader uploader;
    @InjectMocks ProfileImageService service;
    private final UUID userId = UUID.randomUUID();
    private static final long MAX_BYTES = 5 * 1024 * 1024L;

    static byte[] imageBytes(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) throw new IllegalStateException("No image writer for " + format);
        return out.toByteArray();
    }
    private MockMultipartFile image(String format, int width, int height) throws IOException {
        return new MockMultipartFile("file", "avatar." + format, "image/" + format, imageBytes(format, width, height));
    }
    private void uploadSucceeds() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", "https://example.com/avatar.jpg"));
    }
    @ParameterizedTest @ValueSource(strings = {"png", "jpg", "webp"})
    void supportedImagesUploadWithExpectedOptions(String format) throws Exception {
        byte[] bytes;
        if (format.equals("webp")) {
            try (InputStream stream = getClass().getResourceAsStream("/fixtures/1.webp")) {
                assertThat(stream).as("Bundled WebP fixture").isNotNull();
                bytes = stream.readAllBytes();
            }
        } else bytes = imageBytes(format, 100, 100);
        var file = new MockMultipartFile("file", "avatar." + format, "image/" + format, bytes);
        uploadSucceeds();

        assertThat(service.uploadImage(file, userId)).isEqualTo("https://example.com/avatar.jpg");

        @SuppressWarnings("rawtypes") ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(eq(bytes), options.capture());
        Map<?, ?> values = options.getValue();
        assertThat(values.get("public_id")).isEqualTo("profile-images/" + userId + "/avatar");
        assertThat(values.get("overwrite")).isEqualTo(true);
        assertThat(values.get("format")).isEqualTo("jpg");
        assertThat(values.get("quality")).isEqualTo("auto:good");
        String transformation = values.get("transformation").toString();
        assertThat(transformation).contains("w_300", "h_300", "c_fill", "g_face");
    }
    @Test void emptyFileIsRejectedBeforeCloudinary() {
        var file = new MockMultipartFile("file", new byte[0]);
        assertThatThrownBy(() -> service.uploadImage(file, userId)).isInstanceOf(IllegalArgumentException.class).hasMessage("File is empty");
        verifyNoInteractions(cloudinary, uploader);
    }
    @Test void fileLargerThanLimitIsRejected() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(new byte[] {1});
        when(file.getSize()).thenReturn(MAX_BYTES + 1);
        assertThatThrownBy(() -> service.uploadImage(file, userId)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5MB");
        verifyNoInteractions(cloudinary, uploader);
    }
    @Test void exactByteSizeLimitIsAcceptedForDecodableImage() throws Exception {
        // Metadata boundary test: image bytes are small but reported multipart size is exactly the limit.
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(imageBytes("png", 50, 50));
        when(file.getSize()).thenReturn(MAX_BYTES);
        uploadSucceeds();
        assertThat(service.uploadImage(file, userId)).isEqualTo("https://example.com/avatar.jpg");
    }
    @Test void nonImageWithImageExtensionAndMimeTypeIsRejected() {
        var file = new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> service.uploadImage(file, userId)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a valid");
        verifyNoInteractions(cloudinary, uploader);
    }
    @ParameterizedTest @CsvSource({"49,50", "50,49", "4001,50", "50,4001"})
    void rejectsDimensionsOutsideLimits(int width, int height) throws Exception {
        var file = image("png", width, height);
        assertThatThrownBy(() -> service.uploadImage(file, userId)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(width < 50 || height < 50 ? "too small" : "too large");
        verifyNoInteractions(cloudinary, uploader);
    }
    @ParameterizedTest @CsvSource({"50,50", "4000,50", "50,4000"})
    void acceptsDimensionBoundaries(int width, int height) throws Exception {
        uploadSucceeds();
        assertThat(service.uploadImage(image("png", width, height), userId)).isEqualTo("https://example.com/avatar.jpg");
    }
    @Test void fileReadFailureIsWrappedAndDoesNotUpload() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        IOException cause = new IOException("read failed");
        when(file.getBytes()).thenThrow(cause);
        assertThatThrownBy(() -> service.uploadImage(file, userId)).isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to upload profile image").hasCause(cause);
        verifyNoInteractions(cloudinary, uploader);
    }
    @Test void cloudinaryUploadFailureIsWrapped() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        IOException cause = new IOException("upload failed");
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(cause);
        var file = image("png", 50, 50);
        assertThatThrownBy(() -> service.uploadImage(file, userId)).isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to upload profile image").hasCause(cause);
    }
    @Test void deleteUsesStableAvatarId() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        service.deleteImage(userId);
        verify(uploader).destroy("profile-images/" + userId + "/avatar", Map.of());
    }
    @Test void deletionFailureIsWrapped() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        IOException cause = new IOException("delete failed");
        when(uploader.destroy(anyString(), anyMap())).thenThrow(cause);
        assertThatThrownBy(() -> service.deleteImage(userId)).isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to delete profile image").hasCause(cause);
    }
}