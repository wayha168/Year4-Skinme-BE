package com.project.skin_me.controller.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.project.skin_me.repository.ImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Serves product/promotion images at /uploads/{filename}.
 * 1. Tries file on disk first (upload.dir).
 * 2. Falls back to DB (Image by fileName) when file is missing so images work after pull without upload folder.
 * 3. If DB record exists but has no bytes (legacy), returns a placeholder image so the UI does not break.
 */
@RestController
@RequiredArgsConstructor
public class UploadedImageController {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    private final ImageRepository imageRepository;

    /** Minimal 1x1 transparent PNG when DB has record but no image bytes (e.g. legacy data or missing upload folder). */
    private static final byte[] PLACEHOLDER_PNG = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
        (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00,
        0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
        (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<ByteArrayResource> serveUploadedImage(@PathVariable String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/")) {
            return ResponseEntity.badRequest().build();
        }

        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(filename);

        if (Files.isRegularFile(filePath)) {
            try {
                byte[] data = Files.readAllBytes(filePath);
                String contentType = Files.probeContentType(filePath);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(new ByteArrayResource(data));
            } catch (IOException e) {
                return ResponseEntity.notFound().build();
            }
        }

        return imageRepository.findFirstByFileName(filename)
                .map(img -> {
                    if (img.getImage() != null && img.getImage().length > 0) {
                        return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(img.getFileType() != null ? img.getFileType() : "image/png"))
                                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + img.getFileName() + "\"")
                                .body(new ByteArrayResource(img.getImage()));
                    }
                    return ResponseEntity.ok()
                            .contentType(MediaType.IMAGE_PNG)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                            .body(new ByteArrayResource(PLACEHOLDER_PNG));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
