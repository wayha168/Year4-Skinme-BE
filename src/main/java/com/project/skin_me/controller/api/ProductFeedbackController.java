package com.project.skin_me.controller.api;

import com.project.skin_me.dto.ProductFeedbackDto;
import com.project.skin_me.model.User;
import com.project.skin_me.request.ProductFeedbackRequest;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.feedback.IProductFeedbackService;
import com.project.skin_me.service.user.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/feedback")
@RequiredArgsConstructor
public class ProductFeedbackController {

    private final IProductFeedbackService productFeedbackService;
    private final IUserService userService;

    /** Authenticated customers: submit feedback (one per product per user). */
    @PostMapping
    public ResponseEntity<ApiResponse> submit(@Valid @RequestBody ProductFeedbackRequest request) {
        try {
            User user = userService.getAuthenticatedUser();
            ProductFeedbackDto dto = productFeedbackService.submit(user, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse("Feedback submitted", dto));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse(e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(e.getMessage(), null));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    /** Public: approved feedback only for storefront. */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse> listForProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductFeedbackDto> result = productFeedbackService.listVisibleForProduct(productId, pageable);
        return ResponseEntity.ok(new ApiResponse("OK", result));
    }
}
