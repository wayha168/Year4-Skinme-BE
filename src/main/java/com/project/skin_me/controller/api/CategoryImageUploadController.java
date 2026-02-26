package com.project.skin_me.controller.api;

import com.project.skin_me.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/admin")
public class CategoryImageUploadController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final String CATEGORY_SUBDIR = "category";

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/category-image/upload")
    public ResponseEntity<ApiResponse> uploadCategoryImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse("No file provided", null));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse("File must be an image", null));
        }
        try {
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path categoryDir = base.resolve(CATEGORY_SUBDIR);
            Files.createDirectories(categoryDir);

            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString() + (ext != null ? "." + ext : "");
            Path target = categoryDir.resolve(filename);
            file.transferTo(target.toFile());

            // URL that the frontend can use (served by resource handler /uploads/**)
            String url = "/uploads/" + CATEGORY_SUBDIR + "/" + filename;
            return ResponseEntity.ok(new ApiResponse("Upload success", new UploadResult(url)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Upload failed: " + e.getMessage(), null));
        }
    }

    private static String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) return null;
        int i = filename.lastIndexOf('.');
        return i > 0 ? filename.substring(i + 1).toLowerCase() : null;
    }

    public static class UploadResult {
        private final String url;
        public UploadResult(String url) { this.url = url; }
        public String getUrl() { return url; }
    }
}
