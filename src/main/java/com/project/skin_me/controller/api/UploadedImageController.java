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

import com.project.skin_me.model.Image;
import com.project.skin_me.repository.ImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Serves product/promotion images at /uploads/{filename}.
 * Tries file on disk first (upload.dir), then falls back to DB (Image by fileName) for legacy records.
 */
@RestController
@RequiredArgsConstructor
public class UploadedImageController {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    private final ImageRepository imageRepository;

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
                .filter(img -> img.getImage() != null && img.getImage().length > 0)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.getFileType() != null ? img.getFileType() : "application/octet-stream"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + img.getFileName() + "\"")
                        .body(new ByteArrayResource(img.getImage())))
                .orElse(ResponseEntity.notFound().build());
    }
}
