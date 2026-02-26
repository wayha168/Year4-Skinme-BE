package com.project.skin_me.controller.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.project.skin_me.dto.OrderDto;
import com.project.skin_me.dto.OrderStatusCountDto;
import com.project.skin_me.dto.ProductDto;
import com.project.skin_me.dto.SalesMonthDto;
import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.enums.ProductStatus;
import com.project.skin_me.model.Activity;
import com.project.skin_me.model.Brand;
import com.project.skin_me.model.Category;
import com.project.skin_me.model.FavoriteItem;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.Payment;
import com.project.skin_me.model.PopularProduct;
import com.project.skin_me.model.Product;
import com.project.skin_me.model.Role;
import com.project.skin_me.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.skin_me.repository.ActivityRepository;
import com.project.skin_me.repository.ChatMessageRepository;
import com.project.skin_me.repository.FavoriteItemRepository;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.PaymentRepository;
import com.project.skin_me.repository.PopularProductRepository;
import com.project.skin_me.repository.RoleRepository;
import com.project.skin_me.repository.UserRepository;
import com.project.skin_me.request.AddProductRequest;
import com.project.skin_me.request.CreateUserRequest;
import com.project.skin_me.request.ProductUpdateRequest;
import com.project.skin_me.request.UserUpdateRequest;
import com.project.skin_me.service.brand.IBrandService;
import com.project.skin_me.service.category.ICategoryService;
import com.project.skin_me.service.image.IImageService;
import com.project.skin_me.service.notification.NotificationService;
import com.project.skin_me.service.order.IOrderService;
import com.project.skin_me.service.product.IProductService;
import com.project.skin_me.service.user.IUserService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PageController {

    /** Number of items per page; only this many are loaded from DB per request. */
    private static final int PAGE_SIZE = 25;

    private final ICategoryService categoryService;
    private final IBrandService brandService;
    private final IProductService productService;
    private final IImageService imageService;
    private final ChatMessageRepository chatMessageRepository;
    private final IOrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final IUserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActivityRepository activityRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final PopularProductRepository popularProductRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${stripe.public.key}")
    private String stripePublicKey;

    @GetMapping("/login-page")
    public String loginPage(Model model,
            HttpServletRequest request,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String logout) {
        // If user is already authenticated, redirect to dashboard
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() &&
                !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            return "redirect:/dashboard";
        }

        // Ensure CSRF token is available for the standalone login form (required for
        // POST /login)
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken != null) {
            model.addAttribute("_csrf", csrfToken);
        }

        if (expired != null) {
            model.addAttribute("error", "Your session has expired. Please login again.");
        } else if (logout != null) {
            model.addAttribute("success", "You have been logged out successfully.");
        } else if (error != null) {
            model.addAttribute("error", "Invalid email or password. Please try again.");
        }

        return "auth/login";
    }

    @GetMapping("/")
    public String homePage() {
        // Check if user is authenticated
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() &&
                !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            return "redirect:/dashboard";
        }
        return "redirect:/login-page";
    }

    @GetMapping("/logout")
    public String logoutPage() {
        return "redirect:/login-page?logout";
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "reset-password";
    }

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public String dashboard(Model model) {
        try {
            List<Product> products = productService.getAllProducts();
            List<OrderDto> orders = orderService.getAllUserOrders();
            int totalUsers = userService.getAllUsers().size();
            List<Payment> payments = paymentRepository.findAll();
            double totalRevenue = payments.stream()
                    .mapToDouble(p -> p.getAmount() != null ? p.getAmount().doubleValue() : 0)
                    .sum();

            // Calculate new registrations
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
            java.time.LocalDateTime weekStart = now.minusDays(7);
            java.time.LocalDateTime monthStart = now.minusDays(30);

            long newUsersToday = userRepository.findAll().stream()
                    .filter(u -> u.getRegistrationDate() != null &&
                            !u.getRegistrationDate().isBefore(todayStart))
                    .count();

            long newUsersThisWeek = userRepository.findAll().stream()
                    .filter(u -> u.getRegistrationDate() != null &&
                            !u.getRegistrationDate().isBefore(weekStart))
                    .count();

            long newUsersThisMonth = userRepository.findAll().stream()
                    .filter(u -> u.getRegistrationDate() != null &&
                            !u.getRegistrationDate().isBefore(monthStart))
                    .count();

            model.addAttribute("totalProducts", products.size());
            model.addAttribute("totalOrders", orders.size());
            model.addAttribute("totalUsers", totalUsers);
            model.addAttribute("totalRevenue", totalRevenue);
            model.addAttribute("newUsersToday", newUsersToday);
            model.addAttribute("newUsersThisWeek", newUsersThisWeek);
            model.addAttribute("newUsersThisMonth", newUsersThisMonth);

            // Get recent favorite products (latest 5)
            List<FavoriteItem> recentFavorites = favoriteItemRepository.findRecentFavoritesWithRelations();
            List<FavoriteItem> top5Favorites = recentFavorites.stream()
                    .limit(5)
                    .collect(java.util.stream.Collectors.toList());
            model.addAttribute("recentFavorites", top5Favorites);

            // Get popular products (top 5)
            List<PopularProduct> popularProducts = popularProductRepository.findTopPopularProductsWithRelations();
            List<PopularProduct> top5Popular = popularProducts.stream()
                    .limit(5)
                    .collect(java.util.stream.Collectors.toList());
            model.addAttribute("popularProducts", top5Popular);
            model.addAttribute("userOrders", orders);
            model.addAttribute("userPayments", paymentRepository.findAllWithOrderAndUser());

        } catch (Exception e) {
            model.addAttribute("totalProducts", 0);
            model.addAttribute("totalOrders", 0);
            model.addAttribute("totalUsers", 0);
            model.addAttribute("totalRevenue", 0.0);
            model.addAttribute("newUsersToday", 0);
            model.addAttribute("newUsersThisWeek", 0);
            model.addAttribute("newUsersThisMonth", 0);
            model.addAttribute("recentFavorites", List.<FavoriteItem>of());
            model.addAttribute("popularProducts", List.<PopularProduct>of());
            model.addAttribute("userOrders", List.<OrderDto>of());
            model.addAttribute("userPayments", List.<Payment>of());
            model.addAttribute("error", "Failed to load stats: " + e.getMessage());
        }
        model.addAttribute("pageTitle", "Admin Dashboard");
        return "dashboard";
    }

    @GetMapping("/product")
    @PreAuthorize("hasRole('ADMIN')")
    public String productPage(Model model) {
        model.addAttribute("pageTitle", "Products");
        return "product";
    }

    @GetMapping("/view/categories")
    @PreAuthorize("isAuthenticated()")
    public String getAllCategoriesView(Model model) {
        try {
            List<Category> categories = categoryService.getAllCategories();
            model.addAttribute("categories", categories);
            model.addAttribute("pageTitle", "Categories Management");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load categories: " + e.getMessage());
            model.addAttribute("categories", List.<Category>of());
        }
        return "categories";
    }

    @GetMapping("/view/categories/{categoryId}")
    @PreAuthorize("isAuthenticated()")
    public String getCategoryByIdView(@PathVariable Long categoryId, Model model) {
        try {
            Category category = categoryService.getCategoryById(categoryId);
            model.addAttribute("category", category);
            model.addAttribute("pageTitle", "Category Details");
        } catch (Exception e) {
            model.addAttribute("error", "Category not found: " + e.getMessage());
            model.addAttribute("pageTitle", "Category Not Found");
        }
        return "category-details";
    }

    @GetMapping("/views/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public String categoriesPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
            var categoryPage = categoryService.getAllCategories(pageable);
            List<Category> categories = categoryPage.getContent();
            int totalPages = categoryPage.getTotalPages();
            long totalItems = categoryPage.getTotalElements();
            model.addAttribute("categories", categories);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("totalItems", totalItems);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load categories: " + e.getMessage());
            model.addAttribute("categories", List.<Category>of());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalItems", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        model.addAttribute("pageTitle", "Categories Management");
        return "categories";
    }

    // Category CRUD Endpoints
    @GetMapping("/views/categories/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createCategoryPage(Model model) {
        model.addAttribute("pageTitle", "Create Category");
        model.addAttribute("category", null);
        model.addAttribute("error", null);
        return "category-form";
    }

    @GetMapping("/views/categories/createWhitelabel")
    @PreAuthorize("hasRole('ADMIN')")
    public String createWhitelabelCategoryPage(Model model) {
        model.addAttribute("pageTitle", "Create Category");
        model.addAttribute("category", null);
        model.addAttribute("error", null);
        return "category-form";
    }

    @PostMapping("/views/categories/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createCategory(
            @RequestParam String name,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String image,
            @RequestParam(required = false) String link,
            @RequestParam(value = "category-image-upload", required = false) MultipartFile categoryImageUpload,
            Model model) {
        try {
            if (name == null || name.trim().isEmpty()) {
                model.addAttribute("error", "Category name cannot be empty");
                model.addAttribute("category", null);
                model.addAttribute("pageTitle", "Create Category");
                return "category-form";
            }
            Category category = new Category(name.trim());
            category.setTitle(title != null ? title.trim() : null);
            category.setDescription(description != null ? description.trim() : null);
            category.setLink(link != null ? link.trim() : null);
            if (categoryImageUpload != null && !categoryImageUpload.isEmpty()) {
                category.setImage(null);
            } else {
                category.setImage(image != null && !image.isBlank() ? image.trim() : null);
            }
            Category savedCategory = categoryService.addCategory(category);
            if (categoryImageUpload != null && !categoryImageUpload.isEmpty()) {
                String imageUrl = imageService.saveCategoryImage(categoryImageUpload, savedCategory);
                if (imageUrl != null) {
                    savedCategory.setImage(imageUrl);
                    categoryService.updateCategory(savedCategory, savedCategory.getId());
                }
            }

            // Send WebSocket notification for category creation (broadcast to admins)
            try {
                notificationService.broadcastNotification(
                        "New Category Created",
                        "Category '" + savedCategory.getName() + "' has been created.",
                        "PRODUCT");
            } catch (Exception e) {
                // Log but don't fail the category creation
                System.err.println("Failed to send category creation notification: " + e.getMessage());
            }

            return "redirect:/views/categories?success=Category created successfully";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create category: " + e.getMessage());
            model.addAttribute("category", null);
            model.addAttribute("pageTitle", "Create Category");
            return "category-form";
        }
    }

    @GetMapping("/views/categories/{categoryId}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editCategoryPage(@PathVariable Long categoryId, Model model) {
        try {
            Category category = categoryService.getCategoryById(categoryId);
            model.addAttribute("category", category);
            model.addAttribute("pageTitle", "Edit Category");
            return "category-form";
        } catch (Exception e) {
            return "redirect:/views/categories?error=Category not found";
        }
    }

    @PostMapping("/views/categories/{categoryId}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateCategory(
            @PathVariable Long categoryId,
            @RequestParam String name,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String image,
            @RequestParam(required = false) String link,
            @RequestParam(value = "category-image-upload", required = false) MultipartFile categoryImageUpload,
            Model model) {
        try {
            Category existingCategory = categoryService.getCategoryById(categoryId);
            existingCategory.setName(name != null ? name.trim() : existingCategory.getName());
            existingCategory.setTitle(title != null ? title.trim() : existingCategory.getTitle());
            existingCategory
                    .setDescription(description != null ? description.trim() : existingCategory.getDescription());
            existingCategory.setLink(link != null ? link.trim() : existingCategory.getLink());
            if (categoryImageUpload != null && !categoryImageUpload.isEmpty()) {
                String imageUrl = imageService.saveCategoryImage(categoryImageUpload, existingCategory);
                if (imageUrl != null) {
                    existingCategory.setImage(imageUrl);
                }
            } else if (image != null && !image.isBlank()) {
                existingCategory.setImage(image.trim());
            }
            categoryService.updateCategory(existingCategory, categoryId);
            return "redirect:/views/categories?success=Category updated successfully";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to update category: " + e.getMessage());
            model.addAttribute("pageTitle", "Edit Category");
            try {
                Category category = categoryService.getCategoryById(categoryId);
                model.addAttribute("category", category);
            } catch (Exception ex) {
                // Ignore
            }
            return "category-form";
        }
    }

    @PostMapping("/views/categories/{categoryId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteCategory(@PathVariable Long categoryId) {
        try {
            categoryService.deleteCategoryById(categoryId);
            return "redirect:/views/categories?success=Category deleted successfully";
        } catch (Exception e) {
            return "redirect:/views/categories?error=Failed to delete category: " + e.getMessage();
        }
    }

    // Brands list and CRUD
    @GetMapping("/views/brands")
    @PreAuthorize("hasRole('ADMIN')")
    public String brandsListPage(Model model) {
        try {
            List<com.project.skin_me.model.Brand> brands = brandService.getAllBrands();
            model.addAttribute("brands", brands);
            model.addAttribute("pageTitle", "Brands Management");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load brands: " + e.getMessage());
            model.addAttribute("brands", List.<com.project.skin_me.model.Brand>of());
            model.addAttribute("pageTitle", "Brands Management");
        }
        return "brands";
    }

    @GetMapping("/views/brands/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createBrandPage(Model model) {
        List<Category> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("brand", null);
        model.addAttribute("pageTitle", "Create Brand");
        return "brand-form";
    }

    @PostMapping("/views/brands/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createBrand(
            @RequestParam String name,
            @RequestParam Long categoryId,
            @RequestParam(required = false) String imageUrl,
            Model model) {
        try {
            brandService.createBrand(name, imageUrl != null ? imageUrl : "", categoryId);
            return "redirect:/views/brands?success=Brand created successfully";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create brand: " + e.getMessage());
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("brand", null);
            model.addAttribute("pageTitle", "Create Brand");
            return "brand-form";
        }
    }

    @GetMapping("/views/brands/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editBrandPage(@PathVariable Long id, Model model) {
        try {
            com.project.skin_me.model.Brand brand = brandService.getBrandById(id);
            model.addAttribute("brand", brand);
            model.addAttribute("categories", List.<Category>of());
            model.addAttribute("pageTitle", "Edit Brand");
            return "brand-form";
        } catch (Exception e) {
            return "redirect:/views/brands?error=Brand not found";
        }
    }

    @PostMapping("/views/brands/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateBrand(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String imageUrl,
            Model model) {
        try {
            brandService.updateBrand(id, name, imageUrl);
            return "redirect:/views/brands?success=Brand updated successfully";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to update brand: " + e.getMessage());
            try {
                model.addAttribute("brand", brandService.getBrandById(id));
            } catch (Exception ex) {
                return "redirect:/views/brands?error=Brand not found";
            }
            model.addAttribute("pageTitle", "Edit Brand");
            return "brand-form";
        }
    }

    @PostMapping("/views/brands/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteBrand(@PathVariable Long id) {
        try {
            brandService.deleteBrandById(id);
            return "redirect:/views/brands?success=Brand deleted successfully";
        } catch (Exception e) {
            return "redirect:/views/brands?error=Failed to delete brand: " + e.getMessage();
        }
    }

    @GetMapping("/views/products")
    @PreAuthorize("hasRole('ADMIN')")
    public String productsListPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
            var productPage = productService.getAllProducts(pageable);
            List<Product> products = productPage.getContent();
            List<Category> categories = categoryService.getAllCategories();
            int totalPages = productPage.getTotalPages();
            long totalItems = productPage.getTotalElements();
            model.addAttribute("products", products);
            model.addAttribute("categories", categories);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("totalItems", totalItems);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load products: " + e.getMessage());
            model.addAttribute("products", List.<Product>of());
            model.addAttribute("categories", List.<Category>of());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalItems", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        model.addAttribute("pageTitle", "Products Management");
        return "products";
    }

    // Product CRUD Endpoints
    @GetMapping("/views/products/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createProductPage(Model model) {
        try {
            List<Category> categories = categoryService.getAllCategories();
            List<com.project.skin_me.model.Brand> brands = brandService.getAllBrands();
            model.addAttribute("categories", categories);
            model.addAttribute("brands", brands);
            model.addAttribute("product", null);
            model.addAttribute("pageTitle", "Create Product");
            return "product-form";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load form: " + e.getMessage());
            return "redirect:/views/products";
        }
    }

    @PostMapping("/views/products/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createProduct(
            @RequestParam String name,
            @RequestParam java.math.BigDecimal price,
            @RequestParam String productType,
            @RequestParam int inventory,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String howToUse,
            @RequestParam(required = false) String skinType,
            @RequestParam(required = false) String benefit,
            @RequestParam Long brandId,
            @RequestParam(value = "product-images", required = false) List<MultipartFile> productImages,
            Model model) {
        try {
            AddProductRequest request = new AddProductRequest();
            request.setName(name);
            request.setPrice(price);
            request.setProductType(productType);
            request.setInventory(inventory);
            request.setDescription(description != null ? description : "");
            request.setHowToUse(howToUse != null ? howToUse : "");
            request.setSkinType(skinType);
            request.setBenefit(benefit);
            request.setBrandId(brandId);

            Product product = productService.addProduct(request);
            if (productImages != null && !productImages.isEmpty()) {
                imageService.saveImages(product.getId(), productImages);
            }
            return "redirect:/views/products?success=Product created successfully";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create product: " + e.getMessage());
            try {
                List<Category> categories = categoryService.getAllCategories();
                List<com.project.skin_me.model.Brand> brands = brandService.getAllBrands();
                model.addAttribute("categories", categories);
                model.addAttribute("brands", brands);
            } catch (Exception ex) {
                // Ignore
            }
            model.addAttribute("pageTitle", "Create Product");
            return "product-form";
        }
    }

    @GetMapping("/views/products/{productId}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editProductPage(@PathVariable Long productId, Model model) {
        try {
            Product product = productService.getProductById(productId);
            List<Category> categories = categoryService.getAllCategories();
            List<com.project.skin_me.model.Brand> brands = brandService.getAllBrands();
            model.addAttribute("product", product);
            model.addAttribute("categories", categories);
            model.addAttribute("brands", brands);
            model.addAttribute("pageTitle", "Edit Product");
            return "product-form";
        } catch (Exception e) {
            return "redirect:/views/products?error=Product not found";
        }
    }

    @PostMapping("/views/products/{productId}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateProduct(
            @PathVariable Long productId,
            @RequestParam String name,
            @RequestParam java.math.BigDecimal price,
            @RequestParam String productType,
            @RequestParam int inventory,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String howToUse,
            @RequestParam(required = false) String skinType,
            @RequestParam(required = false) String benefit,
            @RequestParam Long brandId,
            @RequestParam(required = false) String status,
            Model model) {
        try {
            ProductUpdateRequest request = new ProductUpdateRequest();
            request.setName(name);
            request.setPrice(price);
            request.setProductType(productType);
            request.setInventory(inventory);
            request.setDescription(description != null ? description : "");
            request.setHowToUse(howToUse != null ? howToUse : "");
            request.setSkinType(skinType);
            request.setBenefit(benefit);
            request.setBrandId(brandId);
            if (status != null) {
                request.setStatus(ProductStatus.valueOf(status));
            }

            productService.updateProduct(request, productId);
            return "redirect:/views/products?success=Product updated successfully";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to update product: " + e.getMessage());
            try {
                Product product = productService.getProductById(productId);
                List<Category> categories = categoryService.getAllCategories();
                List<com.project.skin_me.model.Brand> brands = brandService.getAllBrands();
                model.addAttribute("product", product);
                model.addAttribute("categories", categories);
                model.addAttribute("brands", brands);
            } catch (Exception ex) {
                // Ignore
            }
            model.addAttribute("pageTitle", "Edit Product");
            return "product-form";
        }
    }

    @PostMapping("/views/products/{productId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteProduct(@PathVariable Long productId) {
        try {
            productService.deleteProductById(productId);
            return "redirect:/views/products?success=Product deleted successfully";
        } catch (Exception e) {
            return "redirect:/views/products?error=Failed to delete product: " + e.getMessage();
        }
    }

    @GetMapping("/view/products")
    @PreAuthorize("isAuthenticated()")
    public String getAllProductsView(Model model) {
        try {
            List<Product> products = productService.getAllProducts();
            List<ProductDto> productDtos = productService.getConvertedProducts(products);
            List<Category> categories = categoryService.getAllCategories();
            model.addAttribute("products", productDtos);
            model.addAttribute("categories", categories);
            model.addAttribute("pageTitle", "Products Management");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load products: " + e.getMessage());
            model.addAttribute("products", List.<ProductDto>of());
            model.addAttribute("categories", List.<Category>of());
        }
        return "products";
    }

    @GetMapping("/view/products/{productId}")
    @PreAuthorize("isAuthenticated()")
    public String getProductByIdView(@PathVariable Long productId, Model model) {
        try {
            Product product = productService.getProductById(productId);
            List<Category> categories = categoryService.getAllCategories();
            model.addAttribute("product", product);
            model.addAttribute("categories", categories);
            model.addAttribute("pageTitle", "Product Details");
        } catch (Exception e) {
            model.addAttribute("error", "Product not found: " + e.getMessage());
            model.addAttribute("pageTitle", "Product Not Found");
        }
        return "product-details";
    }

    @GetMapping("/view/products/category/{categoryId}")
    @PreAuthorize("isAuthenticated()")
    public String getProductsByCategory(@PathVariable Long categoryId, Model model) {
        try {
            Category category = categoryService.getCategoryById(categoryId);
            List<Product> products = productService.getProductsByCategoryId(categoryId);
            List<ProductDto> productDtos = productService.getConvertedProducts(products);

            model.addAttribute("products", productDtos);
            model.addAttribute("category", category);
            model.addAttribute("pageTitle", category.getName() + " Products");
            model.addAttribute("pageIcon", "bi-box-seam");
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            model.addAttribute("error", "Category not found: " + e.getMessage());
            model.addAttribute("products", List.<ProductDto>of());
            model.addAttribute("category", null);
            model.addAttribute("pageTitle", "Category Not Found");
            model.addAttribute("pageIcon", "bi-exclamation-triangle");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load products: " + e.getMessage());
            model.addAttribute("products", List.<ProductDto>of());
            model.addAttribute("category", null);
            model.addAttribute("pageTitle", "Products Error");
            model.addAttribute("pageIcon", "bi-exclamation-triangle");
        }
        return "products-by-category";
    }

    @GetMapping("/view/products/brand/{brandId}")
    @PreAuthorize("isAuthenticated()")
    public String getProductsByBrand(@PathVariable Long brandId, Model model) {
        try {
            Brand brand = brandService.getBrandById(brandId);
            List<Product> products = productService.getProductsByBrandId(brandId);
            List<ProductDto> productDtos = productService.getConvertedProducts(products);

            model.addAttribute("products", productDtos);
            model.addAttribute("brand", brand);
            model.addAttribute("pageTitle", brand.getName() + " Products");
            model.addAttribute("pageIcon", "bi-award");
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            model.addAttribute("error", "Brand not found: " + e.getMessage());
            model.addAttribute("products", List.<ProductDto>of());
            model.addAttribute("brand", null);
            model.addAttribute("pageTitle", "Brand Not Found");
            model.addAttribute("pageIcon", "bi-exclamation-triangle");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load products: " + e.getMessage());
            model.addAttribute("products", List.<ProductDto>of());
            model.addAttribute("brand", null);
            model.addAttribute("pageTitle", "Products Error");
            model.addAttribute("pageIcon", "bi-exclamation-triangle");
        }
        return "products-by-brand";
    }

    @GetMapping("/views/products/product-details")
    public String getProductDetailsPage(@RequestParam Long productId, Model model) {
        try {
            Product product = productService.getProductById(productId);
            model.addAttribute("product", product);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load product details: " + e.getMessage());
        }
        model.addAttribute("pageTitle", "Product Details");
        return "product-details";
    }

    @GetMapping("/view/orders")
    @PreAuthorize("isAuthenticated()")
    public String getAllOrdersView(Model model) {
        try {
            List<OrderDto> orderDtos = orderService.getAllUserOrders();
            long completedCount = orderDtos.stream()
                    .filter(o -> o.getOrderStatus() != null && o.getOrderStatus().toString().equals("COMPLETED"))
                    .count();
            long pendingCount = orderDtos.stream()
                    .filter(o -> o.getOrderStatus() != null && o.getOrderStatus().toString().equals("PAYMENT_PENDING"))
                    .count();
            model.addAttribute("orders", orderDtos);
            model.addAttribute("pageTitle", "All Orders");
            model.addAttribute("totalOrders", orderDtos.size());
            model.addAttribute("completedOrders", completedCount);
            model.addAttribute("pendingPaymentOrders", pendingCount);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load orders: " + e.getMessage());
            model.addAttribute("orders", List.<OrderDto>of());
            model.addAttribute("totalOrders", 0);
            model.addAttribute("completedOrders", 0);
            model.addAttribute("pendingPaymentOrders", 0);
        }
        return "orders";
    }

    @GetMapping("/view/orders/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public String getOrderByIdView(@PathVariable Long orderId, Model model) {
        try {
            Order order = orderRepository.findByIdWithOrderItemsAndProducts(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            model.addAttribute("order", order);
            model.addAttribute("pageTitle", "Order Details #" + orderId);
        } catch (Exception e) {
            model.addAttribute("error", "Order not found: " + e.getMessage());
            model.addAttribute("pageTitle", "Order Not Found");
        }
        return "order-details";
    }

    /** Status colors for order charts (could be moved to DB/settings later). */
    private static final Map<OrderStatus, String> ORDER_STATUS_COLORS = new LinkedHashMap<>() {{
        put(OrderStatus.DELIVERED, "#10b981");
        put(OrderStatus.PAID, "#10b981");
        put(OrderStatus.SUCCESS, "#10b981");
        put(OrderStatus.PENDING, "#f59e0b");
        put(OrderStatus.PROCESSING, "#f59e0b");
        put(OrderStatus.PAYMENT_PENDING, "#f97316");
        put(OrderStatus.PAYMENT, "#f97316");
        put(OrderStatus.SHIPPED, "#3b82f6");
        put(OrderStatus.CANCELLED, "#ef4444");
        put(OrderStatus.FAILED, "#ef4444");
    }};

    private static final String DEFAULT_STATUS_COLOR = "#6b7280";

    @GetMapping("/views/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public String ordersListPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("orderId").descending());
            var orderPage = orderService.getAllUserOrders(pageable);
            List<OrderDto> orders = orderPage.getContent();
            long totalOrders = orderPage.getTotalElements();
            long completedOrders = orderRepository.countByOrderStatus(OrderStatus.DELIVERED);
            long pendingPaymentOrders = orderRepository.countByOrderStatus(OrderStatus.PAYMENT_PENDING);
            int totalPages = orderPage.getTotalPages();

            BigDecimal totalRevenue = orderRepository.sumOrderTotalAmount();
            if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
            model.addAttribute("totalRevenue", totalRevenue);

            List<OrderStatusCountDto> orderStatusCounts = new ArrayList<>();
            List<Object[]> statusRows = orderRepository.countGroupByOrderStatus();
            for (Object[] row : statusRows) {
                OrderStatus status = (OrderStatus) row[0];
                Long count = ((Number) row[1]).longValue();
                String color = ORDER_STATUS_COLORS.getOrDefault(status, DEFAULT_STATUS_COLOR);
                orderStatusCounts.add(new OrderStatusCountDto(status.name(), count, color));
            }
            model.addAttribute("orderStatusCounts", orderStatusCounts);

            LocalDate since = LocalDate.now().minusMonths(11).withDayOfMonth(1);
            java.sql.Date sinceSql = java.sql.Date.valueOf(since);
            List<Object[]> revenueByMonth = orderRepository.sumRevenueByMonthSince(sinceSql);
            Map<String, BigDecimal> monthToRevenue = new LinkedHashMap<>();
            for (Object[] row : revenueByMonth) {
                int y = ((Number) row[0]).intValue();
                int m = ((Number) row[1]).intValue();
                BigDecimal sum = BigDecimal.ZERO;
                if (row[2] != null) {
                    sum = row[2] instanceof BigDecimal ? (BigDecimal) row[2] : BigDecimal.valueOf(((Number) row[2]).doubleValue());
                }
                String key = y + "-" + m;
                monthToRevenue.put(key, sum);
            }
            List<SalesMonthDto> salesByMonth = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                LocalDate d = since.plusMonths(i);
                String key = d.getYear() + "-" + d.getMonthValue();
                String label = d.getMonth().name().substring(0, 1) + d.getMonth().name().substring(1).toLowerCase() + " " + d.getYear();
                BigDecimal rev = monthToRevenue.getOrDefault(key, BigDecimal.ZERO);
                salesByMonth.add(new SalesMonthDto(label, rev));
            }
            model.addAttribute("salesByMonth", salesByMonth);
            try {
                model.addAttribute("orderStatusCountsJson", objectMapper.writeValueAsString(orderStatusCounts));
                model.addAttribute("salesByMonthJson", objectMapper.writeValueAsString(salesByMonth));
            } catch (JsonProcessingException e) {
                model.addAttribute("orderStatusCountsJson", "[]");
                model.addAttribute("salesByMonthJson", "[]");
            }

            model.addAttribute("orders", orders);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("completedOrders", completedOrders);
            model.addAttribute("pendingPaymentOrders", pendingPaymentOrders);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load orders: " + e.getMessage());
            model.addAttribute("orders", List.<OrderDto>of());
            model.addAttribute("totalOrders", 0L);
            model.addAttribute("totalRevenue", BigDecimal.ZERO);
            model.addAttribute("orderStatusCounts", List.<OrderStatusCountDto>of());
            model.addAttribute("salesByMonth", List.<SalesMonthDto>of());
            model.addAttribute("orderStatusCountsJson", "[]");
            model.addAttribute("salesByMonthJson", "[]");
            model.addAttribute("completedOrders", 0);
            model.addAttribute("pendingPaymentOrders", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        model.addAttribute("pageTitle", "All Orders");
        return "orders";
    }

    @GetMapping("/view/orders/my-orders")
    @PreAuthorize("hasRole('USER')")
    public String getMyOrdersView(Authentication authentication, Model model) {
        try {
            User user = userService.getAuthenticatedUser();
            List<OrderDto> orderDtos = orderService.getUserOrders(user.getId());
            model.addAttribute("orders", orderDtos);
            model.addAttribute("pageTitle", "My Orders");
            model.addAttribute("totalOrders", orderDtos.size());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load your orders: " + e.getMessage());
            model.addAttribute("orders", List.<OrderDto>of());
            model.addAttribute("totalOrders", 0);
        }
        return "my-orders";
    }

    @GetMapping("/views/my-orders")
    @PreAuthorize("isAuthenticated()")
    public String myOrdersListPage(@RequestParam(defaultValue = "0") int page,
            Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("orderId").descending());
            var orderPage = orderService.getAllUserOrders(pageable);
            List<OrderDto> orders = orderPage.getContent();
            long totalOrders = orderPage.getTotalElements();
            long deliveredOrders = orderRepository.countByOrderStatus(OrderStatus.DELIVERED);
            int totalPages = orderPage.getTotalPages();
            model.addAttribute("orders", orders);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("deliveredOrders", deliveredOrders);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
            model.addAttribute("pageTitle", "User Orders");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load user orders: " + e.getMessage());
            model.addAttribute("orders", List.<OrderDto>of());
            model.addAttribute("totalOrders", 0);
            model.addAttribute("deliveredOrders", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
            model.addAttribute("pageTitle", "User Orders");
        }
        return "my-orders";
    }

    @GetMapping("/view/payments")
    @PreAuthorize("isAuthenticated()")
    public String getAllPaymentsView(Model model) {
        try {
            List<Payment> payments = paymentRepository.findAll();
            model.addAttribute("payments", payments);
            model.addAttribute("pageTitle", "Payments Record");
            model.addAttribute("totalPayments", payments.size());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load payments: " + e.getMessage());
            model.addAttribute("payments", List.<Payment>of());
            model.addAttribute("totalPayments", 0);
        }
        return "payments";
    }

    @GetMapping("/view/payments/{paymentId}")
    @PreAuthorize("isAuthenticated()")
    public String getPaymentByIdView(@PathVariable Long paymentId, Model model) {
        try {
            Payment payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment == null) {
                model.addAttribute("error", "Payment not found");
                model.addAttribute("pageTitle", "Payment Not Found");
                return "payment-details";
            }
            model.addAttribute("payment", payment);
            model.addAttribute("pageTitle", "Payment Details #" + paymentId);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load payment: " + e.getMessage());
            model.addAttribute("pageTitle", "Payment Error");
        }
        return "payment-details";
    }

    @GetMapping("/view/payments/my-payments")
    @PreAuthorize("hasRole('USER')")
    public String getMyPaymentsView(Authentication authentication, Model model) {
        try {
            User user = userService.getAuthenticatedUser();
            List<Payment> payments = paymentRepository.findByOrderUserId(user.getId());
            double totalPaymentAmount = payments.stream()
                    .mapToDouble(p -> p.getAmount() != null ? p.getAmount().doubleValue() : 0)
                    .sum();
            model.addAttribute("payments", payments);
            model.addAttribute("pageTitle", "My Payments");
            model.addAttribute("totalPayments", payments.size());
            model.addAttribute("totalPaymentAmount", totalPaymentAmount);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load your payments: " + e.getMessage());
            model.addAttribute("payments", List.<Payment>of());
            model.addAttribute("totalPayments", 0);
            model.addAttribute("totalPaymentAmount", 0.0);
        }
        return "my-payments";
    }

    @GetMapping("/checkout/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public String checkoutPage(@PathVariable Long orderId, Model model) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found"));
            model.addAttribute("order", order);
            model.addAttribute("pageTitle", "Checkout");
            model.addAttribute("stripePublicKey", stripePublicKey);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load order: " + e.getMessage());
            model.addAttribute("pageTitle", "Checkout Error");
        }
        return "checkout";
    }

    @GetMapping("/views/payments")
    @PreAuthorize("isAuthenticated()")
    public String paymentsListPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").descending());
            var paymentPage = paymentRepository.findAll(pageable);
            List<Payment> payments = paymentPage.getContent();
            long totalPayments = paymentPage.getTotalElements();
            long completedPayments = paymentRepository.countByStatus(OrderStatus.SUCCESS);
            java.math.BigDecimal sum = paymentRepository.sumAllAmounts();
            double totalPaymentAmount = sum != null ? sum.doubleValue() : 0.0;
            int totalPages = paymentPage.getTotalPages();
            model.addAttribute("payments", payments);
            model.addAttribute("totalPayments", totalPayments);
            model.addAttribute("completedPayments", completedPayments);
            model.addAttribute("totalPaymentAmount", totalPaymentAmount);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load payments: " + e.getMessage());
            model.addAttribute("payments", List.<Payment>of());
            model.addAttribute("totalPayments", 0);
            model.addAttribute("completedPayments", 0);
            model.addAttribute("totalPaymentAmount", 0.0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        model.addAttribute("pageTitle", "Payments Record");
        return "payments";
    }

    @GetMapping("/views/my-payments")
    @PreAuthorize("isAuthenticated()")
    public String myPaymentsListPage(@RequestParam(defaultValue = "0") int page,
            Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").descending());
            var paymentPage = paymentRepository.findAllWithOrderAndUser(pageable);
            List<Payment> payments = paymentPage.getContent();
            long totalPayments = paymentPage.getTotalElements();
            java.math.BigDecimal sum = paymentRepository.sumAllAmounts();
            double totalPaymentAmount = sum != null ? sum.doubleValue() : 0.0;
            int totalPages = paymentPage.getTotalPages();
            model.addAttribute("payments", payments);
            model.addAttribute("totalPayments", totalPayments);
            model.addAttribute("totalPaymentAmount", totalPaymentAmount);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
            model.addAttribute("pageTitle", "User Payments");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load user payments: " + e.getMessage());
            model.addAttribute("payments", List.<Payment>of());
            model.addAttribute("totalPayments", 0);
            model.addAttribute("totalPaymentAmount", 0.0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
            model.addAttribute("pageTitle", "User Payments");
        }
        return "my-payments";
    }

    @GetMapping("/views/chat")
    @PreAuthorize("isAuthenticated()")
    public String chatPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            User currentUser = userService.getAuthenticatedUser();
            boolean isAdmin = currentUser.getRoles().stream()
                    .anyMatch(role -> role.getName().equalsIgnoreCase("ADMIN"));

            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("timestamp").descending());
            List<com.project.skin_me.model.ChatMessage> chatHistory;
            int totalPages;
            if (isAdmin) {
                var chatPage = chatMessageRepository.findAllByOrderByTimestampDesc(pageable);
                chatHistory = chatPage.getContent();
                totalPages = chatPage.getTotalPages();
            } else {
                var chatPage = chatMessageRepository.findByUserIdOrderByTimestampDesc(currentUser.getId(), pageable);
                chatHistory = chatPage.getContent();
                totalPages = chatPage.getTotalPages();
            }

            model.addAttribute("chatHistory", chatHistory);
            model.addAttribute("aiResponses", List.<com.project.skin_me.model.ChatMessage>of());
            model.addAttribute("isAdmin", isAdmin);
            model.addAttribute("currentUserId", currentUser.getId());
            model.addAttribute("currentUserEmail", currentUser.getEmail());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load chat: " + e.getMessage());
            model.addAttribute("chatHistory", List.<com.project.skin_me.model.ChatMessage>of());
            model.addAttribute("aiResponses", List.<com.project.skin_me.model.ChatMessage>of());
            model.addAttribute("isAdmin", false);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        model.addAttribute("pageTitle", "Chat");
        return "chat";
    }

    @GetMapping("/views/delivery")
    @PreAuthorize("isAuthenticated()")
    public String deliveryListPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("orderId").descending());
            var orderPage = orderRepository.findAllWithOrderItems(pageable);
            List<Order> deliveries = orderPage.getContent();
            long totalDeliveries = orderPage.getTotalElements();
            long shippedOrders = orderRepository.countByOrderStatus(OrderStatus.SHIPPED)
                    + orderRepository.countByOrderStatus(OrderStatus.DELIVERED);
            long deliveredOrders = orderRepository.countByOrderStatus(OrderStatus.DELIVERED);
            long pendingDeliveries = orderRepository.countByOrderStatus(OrderStatus.PAYMENT_PENDING);
            int totalPages = orderPage.getTotalPages();
            model.addAttribute("deliveries", deliveries);
            model.addAttribute("totalDeliveries", totalDeliveries);
            model.addAttribute("shippedOrders", shippedOrders);
            model.addAttribute("deliveredOrders", deliveredOrders);
            model.addAttribute("pendingDeliveries", pendingDeliveries);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
            model.addAttribute("pageTitle", "Delivery Records");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load deliveries: " + e.getMessage());
            model.addAttribute("deliveries", List.<Order>of());
            model.addAttribute("totalDeliveries", 0);
            model.addAttribute("shippedOrders", 0);
            model.addAttribute("deliveredOrders", 0);
            model.addAttribute("pendingDeliveries", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        return "delivery";
    }

    @GetMapping("/views/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String usersListPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
            var userPage = userRepository.findAll(pageable);
            List<User> users = userPage.getContent();
            long totalUsers = userPage.getTotalElements();
            int totalPages = userPage.getTotalPages();
            List<Role> allRoles = roleRepository.findAll();
            model.addAttribute("users", users);
            model.addAttribute("allRoles", allRoles);
            model.addAttribute("totalUsers", totalUsers);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load users: " + e.getMessage());
            model.addAttribute("users", List.<User>of());
            model.addAttribute("allRoles", List.<Role>of());
            model.addAttribute("totalUsers", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        model.addAttribute("pageTitle", "User Management");
        return "users";
    }

    @GetMapping("/views/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String getUserDetailsPage(@PathVariable Long userId, Model model) {
        try {
            // Load user directly from DB so online status is real
            User loadedUser = userRepository.findById(userId)
                    .orElseThrow(() -> new com.project.skin_me.exception.ResourceNotFoundException(
                            "User not found with ID: " + userId));
            List<Role> roles = roleRepository.findAll();
            model.addAttribute("user", loadedUser);
            model.addAttribute("allRoles", roles);
            model.addAttribute("pageTitle", "User Details");
        } catch (Exception e) {
            model.addAttribute("error", "User not found: " + e.getMessage());
            model.addAttribute("pageTitle", "User Not Found");
        }
        return "user-details";
    }

    @GetMapping("/views/users/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createUserPage(Model model) {
        try {
            List<Role> allRoles = roleRepository.findAll();
            model.addAttribute("allRoles", allRoles);
            model.addAttribute("pageTitle", "Create User");
            model.addAttribute("user", null);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load roles: " + e.getMessage());
        }
        return "user-form";
    }

    @PostMapping("/views/users/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createUser(@ModelAttribute CreateUserRequest request, @RequestParam(required = false) String roleName,
            Model model) {
        try {
            // Handle role assignment from form
            if (roleName != null && !roleName.isEmpty()) {
                com.project.skin_me.dto.RoleDto roleDto = new com.project.skin_me.dto.RoleDto();
                roleDto.setName(roleName);
                request.setRole(roleDto);
            }
            userService.createUser(request);
            return "redirect:/views/users?success=User created successfully";
        } catch (Exception e) {
            List<Role> allRoles = roleRepository.findAll();
            model.addAttribute("allRoles", allRoles);
            model.addAttribute("error", "Failed to create user: " + e.getMessage());
            model.addAttribute("pageTitle", "Create User");
            model.addAttribute("user", null);
            return "user-form";
        }
    }

    @GetMapping("/views/users/{userId}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editUserPage(@PathVariable Long userId, Model model) {
        try {
            User user = userService.getUserById(userId);
            List<Role> allRoles = roleRepository.findAll();
            model.addAttribute("user", user);
            model.addAttribute("allRoles", allRoles);
            model.addAttribute("pageTitle", "Edit User");
        } catch (Exception e) {
            model.addAttribute("error", "User not found: " + e.getMessage());
            model.addAttribute("pageTitle", "User Not Found");
            return "redirect:/views/users";
        }
        return "user-form";
    }

    @PostMapping("/views/users/{userId}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateUser(@PathVariable Long userId, @ModelAttribute UserUpdateRequest request, Model model) {
        try {
            // Handle enabled checkbox - Spring will set it to true if checkbox is checked,
            // null otherwise
            // We need to preserve current state if not provided
            if (request.getEnabled() == null) {
                User currentUser = userService.getUserById(userId);
                request.setEnabled(currentUser.isEnabled());
            }
            userService.updateUser(request, userId);
            return "redirect:/views/users/" + userId + "?success=User updated successfully";
        } catch (Exception e) {
            try {
                User user = userService.getUserById(userId);
                List<Role> allRoles = roleRepository.findAll();
                model.addAttribute("user", user);
                model.addAttribute("allRoles", allRoles);
                model.addAttribute("error", "Failed to update user: " + e.getMessage());
                model.addAttribute("pageTitle", "Edit User");
            } catch (Exception ex) {
                return "redirect:/views/users?error=User not found";
            }
            return "user-form";
        }
    }

    @PostMapping("/views/users/{userId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteUser(@PathVariable Long userId) {
        try {
            userService.deleteUser(userId);
            return "redirect:/views/users?success=User deleted successfully";
        } catch (Exception e) {
            return "redirect:/views/users?error=Failed to delete user: " + e.getMessage();
        }
    }

    @PostMapping("/views/users/{userId}/assign-role")
    @PreAuthorize("hasRole('ADMIN')")
    public String assignRole(@PathVariable Long userId, @RequestParam String roleName) {
        try {
            userService.assignRole(userId, roleName);
            return "redirect:/views/users/" + userId + "?success=Role assigned successfully";
        } catch (Exception e) {
            return "redirect:/views/users/" + userId + "?error=Failed to assign role: " + e.getMessage();
        }
    }

    @PostMapping("/views/users/{userId}/remove-role")
    @PreAuthorize("hasRole('ADMIN')")
    public String removeRole(@PathVariable Long userId, @RequestParam String roleName) {
        try {
            userService.removeRole(userId, roleName);
            return "redirect:/views/users/" + userId + "?success=Role removed successfully";
        } catch (Exception e) {
            return "redirect:/views/users/" + userId + "?error=Failed to remove role: " + e.getMessage();
        }
    }

    // Audit Log Management
    @GetMapping("/views/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public String auditLogsPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String activityType,
            Model model) {
        try {
            List<Activity> activities;

            if (userId != null) {
                activities = activityRepository.findByUserIdOrderByTimestampDesc(userId);
            } else if (activityType != null && !activityType.isEmpty()) {
                try {
                    com.project.skin_me.enums.ActivityType type = com.project.skin_me.enums.ActivityType
                            .valueOf(activityType.toUpperCase());
                    activities = activityRepository.findByActivityType(type);
                    activities.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
                } catch (IllegalArgumentException e) {
                    activities = activityRepository.findAllWithUserOrderByTimestampDesc();
                }
            } else {
                activities = activityRepository.findAllWithUserOrderByTimestampDesc();
            }

            model.addAttribute("activities", activities);
            model.addAttribute("totalActivities", activities.size());
            model.addAttribute("pageTitle", "Audit Log Management");

            // Get filter options
            List<User> allUsers = userRepository.findAll();
            model.addAttribute("allUsers", allUsers);

        } catch (Exception e) {
            model.addAttribute("error", "Failed to load audit logs: " + e.getMessage());
            model.addAttribute("activities", List.<Activity>of());
            model.addAttribute("totalActivities", 0);
        }
        return "audit-logs";
    }

}
