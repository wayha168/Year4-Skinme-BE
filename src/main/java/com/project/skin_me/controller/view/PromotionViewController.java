package com.project.skin_me.controller.view;

import com.project.skin_me.dto.ProductOptionDto;
import com.project.skin_me.dto.PromotionDto;
import com.project.skin_me.model.Product;
import com.project.skin_me.repository.ProductRepository;
import com.project.skin_me.service.promotion.IPromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/views/promotions")
public class PromotionViewController {

    private static final int PAGE_SIZE = 25;

    private final IPromotionService promotionService;
    private final ProductRepository productRepository;

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String promotionsPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("createdAt").descending());
            var promotionPage = promotionService.getAllPromotions(pageable);
            List<PromotionDto> promotions = promotionPage.getContent();
            List<Product> products = productRepository.findAll();
            int totalPages = promotionPage.getTotalPages();
            long totalItems = promotionPage.getTotalElements();
            model.addAttribute("promotions", promotions);
            model.addAttribute("products", products);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("totalItems", totalItems);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
            model.addAttribute("pageTitle", "Promotion Management");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load promotions: " + e.getMessage());
            model.addAttribute("promotions", List.<PromotionDto>of());
            model.addAttribute("products", List.<Product>of());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalItems", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        return "promotions";
    }

    @GetMapping("/create")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String createPromotionPage(@RequestParam(required = false) Long id, Model model) {
        model.addAttribute("editingId", id);
        model.addAttribute("promotion", (PromotionDto) null);
        model.addAttribute("pageTitle", id != null ? "Edit Promotion" : "Create Promotion");
        try {
            model.addAttribute("products", productRepository.findAllProductOptions());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load page: " + e.getMessage());
            model.addAttribute("products", List.<ProductOptionDto>of());
        }
        if (id != null) {
            try {
                PromotionDto promotion = promotionService.getPromotionById(id);
                model.addAttribute("promotion", promotion);
            } catch (Exception e) {
                model.addAttribute("error", "Failed to load promotion: " + e.getMessage());
            }
        }
        return "promotion-form";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String promotionDetailsPage(@PathVariable Long id, Model model) {
        try {
            PromotionDto promotion = promotionService.getPromotionById(id);
            List<Product> products = productRepository.findAll();
            model.addAttribute("promotion", promotion);
            model.addAttribute("products", products);
            model.addAttribute("pageTitle", "Promotion Details");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load promotion: " + e.getMessage());
            model.addAttribute("pageTitle", "Promotion Error");
        }
        return "promotion-details";
    }
}
