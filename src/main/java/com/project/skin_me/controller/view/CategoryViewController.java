package com.project.skin_me.controller.view;

import com.project.skin_me.model.Category;
import com.project.skin_me.service.category.ICategoryService;
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
@RequestMapping("/view/categories")
public class CategoryViewController {

    private final ICategoryService categoryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String getAllCategories(Model model) {
        try {
            List<Category> categories = categoryService.getAllCategories();
            model.addAttribute("categories", categories);
            model.addAttribute("pageTitle", "Categories Management");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load categories: " + e.getMessage());
        }
        return "categories";
    }

    @GetMapping("/{categoryId}")
    @PreAuthorize("isAuthenticated()")
    public String getCategoryById(@PathVariable Long categoryId, Model model) {
        try {
            Category category = categoryService.getCategoryById(categoryId);
            model.addAttribute("category", category);
            model.addAttribute("pageTitle", "Category Details");
        } catch (Exception e) {
            model.addAttribute("error", "Category not found: " + e.getMessage());
        }
        return "category-details";
    }
}
