package com.project.skin_me.controller.api;

import com.project.skin_me.dto.ImageDto;
import com.project.skin_me.dto.PromotionDto;
import com.project.skin_me.request.CreatePromotionRequest;
import com.project.skin_me.request.UpdatePromotionRequest;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.promotion.IPromotionService;
import com.project.skin_me.service.promotion.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/promotions")
public class PromotionController {

    private static final Logger logger = LoggerFactory.getLogger(PromotionController.class);
    private final IPromotionService promotionService;
    private final PromotionService promotionServiceImpl;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> createPromotion(@Valid @RequestBody CreatePromotionRequest request) {
        try {
            logger.debug("Creating promotion: {}", request.getTitle());
            PromotionDto promotion = promotionService.createPromotion(request);
            return ResponseEntity.status(CREATED)
                    .body(new ApiResponse("Promotion created successfully", promotion));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid promotion data: {}", e.getMessage());
            return ResponseEntity.status(BAD_REQUEST)
                    .body(new ApiResponse("Invalid promotion data: " + e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error creating promotion: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error creating promotion: " + e.getMessage(), null));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getPromotionById(@PathVariable Long id) {
        try {
            PromotionDto promotion = promotionService.getPromotionById(id);
            return ResponseEntity.ok(new ApiResponse("Promotion retrieved successfully", promotion));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Promotion not found", null));
        } catch (Exception e) {
            logger.error("Error retrieving promotion: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving promotion: " + e.getMessage(), null));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse> getAllPromotions() {
        try {
            List<PromotionDto> promotions = promotionService.getAllPromotions();
            return ResponseEntity.ok(new ApiResponse("Promotions retrieved successfully", promotions));
        } catch (Exception e) {
            logger.error("Error retrieving promotions: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving promotions: " + e.getMessage(), null));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse> getActivePromotions() {
        try {
            List<PromotionDto> promotions = promotionService.getActivePromotions();
            return ResponseEntity.ok(new ApiResponse("Active promotions retrieved successfully", promotions));
        } catch (Exception e) {
            logger.error("Error retrieving active promotions: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving active promotions: " + e.getMessage(), null));
        }
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse> getActivePromotionByProductId(@PathVariable Long productId) {
        try {
            PromotionDto promotion = promotionService.getActivePromotionByProductId(productId);
            return ResponseEntity.ok(new ApiResponse("Active promotion retrieved successfully", promotion));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("No active promotion found for this product", null));
        } catch (Exception e) {
            logger.error("Error retrieving promotion for product: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving promotion: " + e.getMessage(), null));
        }
    }

    @GetMapping("/product/{productId}/discounted-price")
    public ResponseEntity<ApiResponse> getDiscountedPrice(@PathVariable Long productId) {
        try {
            BigDecimal discountedPrice = promotionService.calculateDiscountedPrice(productId);
            return ResponseEntity.ok(new ApiResponse("Discounted price calculated successfully", discountedPrice));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Product not found", null));
        } catch (Exception e) {
            logger.error("Error calculating discounted price: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error calculating discounted price: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> updatePromotion(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePromotionRequest request) {
        try {
            logger.debug("Updating promotion: {}", id);
            PromotionDto promotion = promotionService.updatePromotion(id, request);
            return ResponseEntity.ok(new ApiResponse("Promotion updated successfully", promotion));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Promotion not found", null));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid promotion data: {}", e.getMessage());
            return ResponseEntity.status(BAD_REQUEST)
                    .body(new ApiResponse("Invalid promotion data: " + e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error updating promotion: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error updating promotion: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> deletePromotion(@PathVariable Long id) {
        try {
            logger.debug("Deleting promotion: {}", id);
            promotionService.deletePromotion(id);
            return ResponseEntity.ok(new ApiResponse("Promotion deleted successfully", null));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Promotion not found", null));
        } catch (Exception e) {
            logger.error("Error deleting promotion: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error deleting promotion: " + e.getMessage(), null));
        }
    }

    @PostMapping("/{promotionId}/images")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> uploadPromotionImages(
            @PathVariable Long promotionId,
            @RequestParam List<MultipartFile> files) {
        try {
            logger.debug("Uploading images for promotion: {}", promotionId);
            List<ImageDto> imageDtos = promotionServiceImpl.savePromotionImages(promotionId, files);
            return ResponseEntity.status(CREATED)
                    .body(new ApiResponse("Images uploaded successfully", imageDtos));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Promotion not found", null));
        } catch (Exception e) {
            logger.error("Error uploading images: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error uploading images: " + e.getMessage(), null));
        }
    }
}
