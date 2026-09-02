package com.minthanttun.usermanagementsystem.user;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.cloudinary.Transformation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

    private final Cloudinary cloudinary;

    public String uploadImage(MultipartFile file, UUID userId) {
        validate(file);

        try {
            Map<String, Object> uploadOptions = ObjectUtils.asMap(
                    "public_id", "profile-images/" + userId,
                    "overwrite", true,
                    "transformation", new Transformation()
                            .width(300)
                            .height(300)
                            .crop("fill")
                            .gravity("face")
            );

            Map uploadResult = cloudinary.uploader()
                    .upload(file.getBytes(), uploadOptions);

            return (String) uploadResult.get("secure_url");

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload profile image", e);
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum size of 5MB");
        }

        String contentType = file.getContentType();
        boolean validContentType = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());

        String filename = file.getOriginalFilename();
        String extension = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "";
        boolean validExtension = ALLOWED_EXTENSIONS.contains(extension);

        if (!validContentType && !validExtension) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are allowed");
        }
    }
}