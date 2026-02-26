package com.project.skin_me.controller.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.project.skin_me.exception.AlreadyExistsException;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Category;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.category.ICategoryService;
import com.project.skin_me.service.image.IImageService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/categories")
public class CategoryController {

    private final ICategoryService categoryService;
    private final IImageService imageService;

    @GetMapping("/all-categories")
    public ResponseEntity<ApiResponse> getAllCategories() {
        try {
            List<Category> categories = categoryService.getAllCategories();
            return ResponseEntity.ok(new ApiResponse("Success!", categories));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error:", INTERNAL_SERVER_ERROR));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/add-category")
    public ResponseEntity<ApiResponse> addCategory(@RequestBody Category name) {
        try {
            Category theCategory = categoryService.addCategory(name);
            return ResponseEntity.ok(new ApiResponse("Success", theCategory));
        } catch (AlreadyExistsException e) {
            return ResponseEntity.status(CONFLICT).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/category/{id}/category")
    public ResponseEntity<ApiResponse> getCategoryById(@PathVariable Long id) {
        try {
            Category theCategory = categoryService.getCategoryById(id);
            return ResponseEntity.ok(new ApiResponse("Found:", theCategory));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/category/{name}/category")
    public ResponseEntity<ApiResponse> getCategoryByName(@PathVariable String name) {
        try {
            Category theCategory = categoryService.getCategoryByName(name);
            return ResponseEntity.ok(new ApiResponse("Found:", theCategory));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/category/{id}/delete")
    public ResponseEntity<ApiResponse> deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategoryById(id);
            return ResponseEntity.ok(new ApiResponse("Delete:", null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/category/{id}/update")
    public ResponseEntity<ApiResponse> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        try {
            Category updatedCategory = categoryService.updateCategory(category, id);
            return ResponseEntity.ok(new ApiResponse("Update success!", updatedCategory));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(BAD_REQUEST).body(new ApiResponse(e.getMessage(), null));
        }
    }

    /**
     * Upload image for a category (same as product images). Sets category.image to
     * the served URL (e.g. /uploads/filename).
     * Use when creating/editing via API: create category first, then POST image
     * here.
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/category/{id}/image")
    public ResponseEntity<ApiResponse> uploadCategoryImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.status(BAD_REQUEST).body(new ApiResponse("No file provided", null));
            }
            Category category = categoryService.getCategoryById(id);
            String imageUrl = imageService.saveCategoryImage(file, category);
            if (imageUrl == null) {
                return ResponseEntity.status(BAD_REQUEST).body(new ApiResponse("Failed to save image", null));
            }
            category.setImage(imageUrl);
            Category updated = categoryService.updateCategory(category, id);
            return ResponseEntity
                    .ok(new ApiResponse("Image uploaded", Map.of("category", updated, "imageUrl", imageUrl)));
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException) {
                return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
            }
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Upload failed: " + e.getMessage(), null));
        }
    }

}
