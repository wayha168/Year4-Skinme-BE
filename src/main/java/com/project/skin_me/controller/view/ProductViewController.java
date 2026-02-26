package com.project.skin_me.controller.view;

import com.project.skin_me.dto.ProductDto;
import com.project.skin_me.model.Category;
import com.project.skin_me.model.Product;
import com.project.skin_me.service.category.ICategoryService;
import com.project.skin_me.service.product.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

// @Controller - Disabled: Routes consolidated into PageController
@RequiredArgsConstructor
@RequestMapping("/view/products")
public class ProductViewController {

    private final IProductService productService;
    private final ICategoryService categoryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String getAllProducts(Model model) {
        try {
            List<Product> products = productService.getAllProducts();
            List<ProductDto> productDtos = productService.getConvertedProducts(products);
            List<Category> categories = categoryService.getAllCategories();

            model.addAttribute("products", productDtos);
            model.addAttribute("categories", categories);
            model.addAttribute("pageTitle", "Products Management");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load products: " + e.getMessage());
        }
        return "products";
    }

    @GetMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    public String getProductById(@PathVariable Long productId, Model model) {
        try {
            Product product = productService.getProductByIdWithDetails(productId);
            List<Category> categories = categoryService.getAllCategories();

            model.addAttribute("product", product);
            model.addAttribute("categories", categories);
            model.addAttribute("pageTitle", "Product Details");
        } catch (Exception e) {
            model.addAttribute("error", "Product not found: " + e.getMessage());
        }
        return "product-details";
    }

    @GetMapping("/category/{categoryId}")
    public String getProductsByCategory(@PathVariable Long categoryId, Model model) {
        try {
            Category category = categoryService.getCategoryById(categoryId);
            List<Product> products = productService.getAllProductsByCategory(category.getName());
            List<ProductDto> productDtos = productService.getConvertedProducts(products);

            model.addAttribute("products", productDtos);
            model.addAttribute("category", category);
            model.addAttribute("pageTitle", category.getName() + " Products");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load products: " + e.getMessage());
        }
        return "products-by-category";
    }
}
