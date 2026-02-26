package com.project.skin_me.service.product;

import java.util.List;

import com.project.skin_me.dto.ProductDto;
import com.project.skin_me.model.Product;
import com.project.skin_me.request.AddProductRequest;
import com.project.skin_me.request.ProductUpdateRequest;

public interface IProductService {
    
    Product addProduct(AddProductRequest request);

    Product getProductById(Long id);

    /** Product with brand, category, and images loaded (for detail view). */
    Product getProductByIdWithDetails(Long id);

    void deleteProductById(Long id);

    List<Product> getActiveProducts();

    Product updateProduct(ProductUpdateRequest product, Long productId);

    List<Product> getAllProducts();

    /** Products with category, brand, and images loaded (for list/detail views). */
    List<Product> getAllProductsWithImages();

    List<Product> getAllProductsByCategory(String category);

    List<Product> getProductsByCategoryId(Long categoryId);

    List<Product> getProductsByBrand(String brand);

    List<Product> getProductsByProductType(String productType);

    List<Product> getProductsByCategoryAndBrand(String category, String brand);

    // List<Product> getProductsByCategoryAndProductType(String category, String
    // productType);

    List<Product> getProductsByName(String name);

    List<Product> getProductsByBrandAndName(String brand, String name);

    List<Product> getProductsByProductTypeAndName(String productType, String name);

    Long countProductsByBrandAndName(String brand, String name);

    ProductDto convertToDto(Product product);

    List<ProductDto> getConvertedProducts(List<Product> products);

    List<Product> getPopularProducts();

    String toMarkdownTable(List<Product> products);

    /** Export all products as CSV (header + rows, proper escaping). */
    String toCsv(List<Product> products);

    List<Product> getAllProductsWithoutImages();

}
