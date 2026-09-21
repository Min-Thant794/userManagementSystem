package com.minthanttun.usermanagementsystem.user;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.cloudinary.Transformation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final int MIN_DIMENSION_PX = 50;
    private static final int MAX_DIMENSION_PX = 4000;

    private final Cloudinary cloudinary;

    public String uploadImage(MultipartFile file, UUID userId) {
        try {
            byte[] bytes = file.getBytes();
            validateAndDecode(file, bytes);

            Map<String, Object> uploadOptions = ObjectUtils.asMap(
                    "public_id", avatarPublicId(userId),
                    "overwrite", true,
                    "format", "jpg",
                    "quality", "auto:good",
                    "transformation", new Transformation()
                            .width(300)
                            .height(300)
                            .crop("fill")
                            .gravity("face")
            );

            Map uploadResult = cloudinary.uploader().upload(bytes, uploadOptions);
            return (String) uploadResult.get("secure_url");

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload profile image", e);
        }
    }

    public void deleteImage(UUID userId) {
        try {
            cloudinary.uploader().destroy(avatarPublicId(userId), ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete profile image", e);
        }
    }

    private void validateAndDecode(MultipartFile file, byte[] bytes) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum size of 5MB");
        }

        BufferedImage image;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
             ImageInputStream imageInput = ImageIO.createImageInputStream(stream)) {
            if (imageInput == null) {
                throw new IllegalArgumentException("File is not a valid JPEG, PNG, or WebP image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("File is not a valid JPEG, PNG, or WebP image");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png", "webp").contains(format)) {
                    throw new IllegalArgumentException("Only JPEG, PNG, or WebP images are allowed");
                }
                reader.setInput(imageInput);
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        }

        if (image == null) {
            throw new IllegalArgumentException("File is not a valid JPEG, PNG, or WebP image");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width < MIN_DIMENSION_PX || height < MIN_DIMENSION_PX) {
            throw new IllegalArgumentException("Image is too small (minimum " + MIN_DIMENSION_PX + "x" + MIN_DIMENSION_PX + "px");
        }

        if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX) {
            throw new IllegalArgumentException("Image is too large (maximum " + MAX_DIMENSION_PX + "x" + MAX_DIMENSION_PX + "px");
        }
    }

    private String avatarPublicId(UUID userId) {
        return "profile-images/" + userId + "/avatar";
    }
}