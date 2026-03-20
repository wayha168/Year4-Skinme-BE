package com.project.skin_me.controller.api;

import com.project.skin_me.dto.ProductDto;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.product.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public product detail path used by notification {@code actionUrl} ({@code /products/{id}}).
 * Same payload as the versioned API, including {@link ProductDto#getFavoriteCount()}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductPublicController {

    private final IProductService productService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse> getProductById(@PathVariable Long productId) {
        try {
            ProductDto dto = productService.getProductDtoByIdWithFavoriteCount(productId);
            return ResponseEntity.ok(new ApiResponse("success", dto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }
}
