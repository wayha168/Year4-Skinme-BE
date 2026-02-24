package com.project.skin_me.controller.api;

import com.project.skin_me.dto.PopularProductDto;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.popularProduct.PopularProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/popular")
public class PopularProductController {

    private final PopularProductService popularProductService;

    @GetMapping("/all")
    public ResponseEntity<ApiResponse> getSalesRecords() {
        try {
            List<PopularProductDto> popularProducts = popularProductService.getPopularProducts();
            if (popularProducts.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse("No products with sales over 10 units Success!", popularProducts));
            }
            return ResponseEntity.ok(new ApiResponse("success", popularProducts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving sales records: " + e.getMessage(), null));
        }
    }

    @GetMapping("/sales")
    public ResponseEntity<ApiResponse> getAllSalesRecords() {
        try {
            List<PopularProductDto> allProducts = popularProductService.getAllProductsWithSales();
            return ResponseEntity.ok(new ApiResponse("success", allProducts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving sales records: " + e.getMessage(), null));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse> getUserSalesRecords(@PathVariable Long userId) {
        try {
            List<PopularProductDto> userProducts = popularProductService.getUserSales(userId);
            return ResponseEntity.ok(new ApiResponse("success", userProducts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving user sales records: " + e.getMessage(), null));
        }
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse> getSalesRecordByProductId(@PathVariable Long productId) {
        try {
            return popularProductService.findByProductId(productId)
                    .map(popularProduct -> {
                        if (popularProduct.getQuantitySold() >= 10) {
                            PopularProductDto dto = popularProductService.convertToDto(popularProduct);
                            return ResponseEntity.ok(new ApiResponse("success", dto));
                        } else {
                            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                    .body(new ApiResponse("Product is not popular (sales < 10)", null));
                        }
                    })
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponse("No sales record found for product ID: " + productId, null)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving sales record: " + e.getMessage(), null));
        }
    }
}
