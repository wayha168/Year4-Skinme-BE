package com.project.skin_me.controller.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.skin_me.dto.ProductDto;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Product;
import com.project.skin_me.request.AddProductRequest;
import com.project.skin_me.request.ProductUpdateRequest;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.product.IProductService;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/products")
public class ProductController {

    private final IProductService productService;

    @GetMapping("/all")
    public ResponseEntity<ApiResponse> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        List<ProductDto> convertedProducts = productService.getConvertedProducts(products);

        return ResponseEntity.ok(new ApiResponse("success", convertedProducts));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/add")
    public ResponseEntity<ApiResponse> addProduct(@RequestBody AddProductRequest product) {
        try {
            Product theProduct = productService.addProduct(product);
            return ResponseEntity.ok(new ApiResponse("Add product success", theProduct));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/product/{productId}/update")
    public ResponseEntity<ApiResponse> updateProduct(@RequestBody ProductUpdateRequest productUpdateRequest,
                                                     @PathVariable Long productId) {
        try {
            Product theProduct = productService.updateProduct(productUpdateRequest, productId);
            return ResponseEntity.ok(new ApiResponse("Update successfully", theProduct));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/product/{productId}/delete")
    public ResponseEntity<ApiResponse> deleteProducts(@PathVariable Long productId) {
        try {
            productService.deleteProductById(productId);
            return ResponseEntity.ok(new ApiResponse("Delete product success", productId));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("product/{productId}/product")
    public ResponseEntity<ApiResponse> getProductById(@PathVariable Long productId) {
        try {
            Product products = productService.getProductById(productId);
            ProductDto productDto = productService.convertToDto(products);

            return ResponseEntity.ok(new ApiResponse("sucsess", productDto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }

    }

    @GetMapping("product/by-product-type")
    public ResponseEntity<ApiResponse> findProductsByProductType(@RequestParam String productType) {
        try {
            if (productType == null || productType.isBlank()) {
                return ResponseEntity.badRequest().body(new ApiResponse("ProductType must be provided!", null));
            }
            List<Product> products = productService.getProductsByProductType(productType);
            List<ProductDto> convertedProducts = productService.getConvertedProducts(products);

            if (products.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse("No products found!", null));
            }
            return ResponseEntity.ok(new ApiResponse("sucsess", convertedProducts));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }

    }

    @GetMapping("product/by-name")
    public ResponseEntity<ApiResponse> findProductsByName(@RequestParam String name) {
        try {
            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest().body(new ApiResponse("No product by this name!", null));
            }
            List<Product> products = productService.getProductsByName(name);
            List<ProductDto> convertedProducts = productService.getConvertedProducts(products);

            if (products.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse("No products found!", null));
            }
            return ResponseEntity.ok(new ApiResponse("success", convertedProducts));
        }  catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/product-active")
    public ResponseEntity<ApiResponse> getActiveProducts() {
        List<Product> activeProducts = productService.getActiveProducts();
        List<ProductDto> convertedProducts = productService.getConvertedProducts(activeProducts);

        if (activeProducts.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse("No active products found!", null));
        }

        return ResponseEntity.ok(new ApiResponse("success", convertedProducts));
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<ApiResponse> getProductsByCategory(@PathVariable String category) {
        try {
            if (category == null || category.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse("Category name must be provided!", null));
            }

            List<Product> products = productService.getAllProductsByCategory(category);
            if (products.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse("No products found for category: " + category, null));
            }

            List<ProductDto> convertedProducts = productService.getConvertedProducts(products);
            return ResponseEntity.ok(new ApiResponse("success", convertedProducts));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving products by category: " + e.getMessage(), null));
        }
    }

    @GetMapping("/by-brand/{brand}")
    public ResponseEntity<ApiResponse> getProductsByBrand(@PathVariable String brand) {
        try {
            if (brand == null || brand.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse("Brand name must be provided!", null));
            }

            List<Product> products = productService.getProductsByBrand(brand);
            if (products.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse("No products found for brand: " + brand, null));
            }

            List<ProductDto> convertedProducts = productService.getConvertedProducts(products);
            return ResponseEntity.ok(new ApiResponse("success", convertedProducts));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving products by brand: " + e.getMessage(), null));
        }
    }

    @GetMapping("/product/by-brand-and-name/")
    public ResponseEntity<ApiResponse> findProductByBrandAndName(@RequestParam String productName,
            @RequestParam String productBrand) {
        try {
            List<Product> products = productService.getProductsByBrandAndName(productName, productBrand);
            List<ProductDto> convertedProducts = productService.getConvertedProducts(products);

            if (products.isEmpty()) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse("success", null));
            }
            return ResponseEntity.ok(new ApiResponse("success", convertedProducts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("products/by-product-type-and-product-name/")
    public ResponseEntity<ApiResponse> getProductsByProductTypeAndName(@RequestParam String productType,
            @RequestParam String productName) {
        try {
            List<Product> products = productService.getProductsByProductTypeAndName(productType, productName);
            List<ProductDto> convertedProducts = productService.getConvertedProducts(products);

            if (products.isEmpty()) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse("success", null));
            }

            return ResponseEntity.ok(new ApiResponse("success", convertedProducts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/by/category-and-brand/")
    public ResponseEntity<ApiResponse> getProductsByCategoryAndBrand(@RequestParam String category,
            @RequestParam String productBrand) {
        try {
            List<Product> products = productService.getProductsByProductTypeAndName(category, productBrand);
            List<ProductDto> convertedProducts = productService.getConvertedProducts(products);

            if (products.isEmpty()) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse("No product found!", null));
            }

            return ResponseEntity.ok(new ApiResponse("success", convertedProducts));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/product/count/by-brand-and-name/")
    public ResponseEntity<ApiResponse> countProductsByBrandAndName(@RequestParam String brand,
            @RequestParam String name) {
        try {
            var productsCount = productService.countProductsByBrandAndName(brand, name);
            return ResponseEntity.ok(new ApiResponse("Product count!", productsCount));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse> getPopularProducts() {
        try {
            List<Product> popularProducts = productService.getPopularProducts();
            List<ProductDto> convertedProducts = productService.getConvertedProducts(popularProducts);

            if (popularProducts.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse("No popular products found!", null));
            }

            return ResponseEntity.ok(new ApiResponse("success", convertedProducts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving popular products: " + e.getMessage(), null));
        }
    }

    @GetMapping(value = "/markdown", produces = "text/markdown")
    public ResponseEntity<String> getProductsMarkdown(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String productType) {

        List<Product> products;

        if (category != null && brand != null) {
            products = productService.getProductsByCategoryAndBrand(category, brand);
        } else if (brand != null && name != null) {
            products = productService.getProductsByBrandAndName(brand, name);
        } else if (category != null) {
            products = productService.getAllProductsByCategory(category);
        } else if (brand != null) {
            products = productService.getProductsByBrand(brand);
        } else if (name != null) {
            products = productService.getProductsByName(name);
        } else if (productType != null) {
            products = productService.getProductsByProductType(productType);
        } else {
            products = productService.getAllProducts();
        }

        String markdown = productService.toMarkdownTable(products);
        return ResponseEntity.ok()
                .header("Content-Type", "text/markdown")
                .body(markdown);
    }

    // @GetMapping("/by-category-and-type/")
    // public ResponseEntity<ApiResponse>
    // getProductsByCategoryAndProductType(@RequestParam String category,
    // @RequestParam String productType) {
    // try {
    // List<Product> products =
    // productService.getProductsByCategoryAndProductType(category, productType);
    // if (products.isEmpty()) {

    // return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse("No
    // product found!", null));
    // }

    // return ResponseEntity.ok(new ApiResponse("success", products));
    // } catch (Exception e) {
    // return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new
    // ApiResponse(e.getMessage(), null));
    // }
    // }

}
