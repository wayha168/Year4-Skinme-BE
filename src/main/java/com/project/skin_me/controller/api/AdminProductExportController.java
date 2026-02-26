package com.project.skin_me.controller.api;

import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.product.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/admin")
public class AdminProductExportController {

    private final IProductService productService;

    private static final Path EXPORT_DIR = Paths.get("exported-catalog");
    private static final String FILE_NAME = "product-catalog.md";

    static {
        try {
            Files.createDirectories(EXPORT_DIR);
        } catch (Exception ignored) {
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/products/export-markdown")
    public ResponseEntity<ApiResponse> exportCatalogMarkdown() {
        try {
            var products = productService.getAllProducts();
            String markdown = productService.toMarkdownTable(products);

            Path filePath = EXPORT_DIR.resolve(FILE_NAME);
            Files.writeString(filePath, markdown);

            // optional: also return the file as attachment
            Resource resource = new UrlResource(filePath.toUri());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + FILE_NAME + "\"")
                    .contentType(MediaType.parseMediaType("text/markdown"))
                    .body(new ApiResponse("Catalog exported to " + filePath.toAbsolutePath(), resource));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Export failed: " + e.getMessage(), null));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/products/export-csv")
    public ResponseEntity<byte[]> exportProductsCsv() {
        try {
            var products = productService.getAllProducts();
            String csv = productService.toCsv(products);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", "products.csv");
            headers.setContentLength(bytes.length);
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/products/markdown-file")
    public ResponseEntity<Resource> downloadExportedFile() throws Exception {
        Path filePath = EXPORT_DIR.resolve(FILE_NAME);
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }
        Resource resource = new UrlResource(filePath.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + FILE_NAME + "\"")
                .contentType(MediaType.parseMediaType("text/markdown"))
                .body(resource);
    }
}
