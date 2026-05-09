package com.project.skin_me.controller.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
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
import com.project.skin_me.dto.ProductFeedbackDto;
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
import com.project.skin_me.repository.ChatAiRepository;
import com.project.skin_me.repository.ChatMessageRepository;
import com.project.skin_me.repository.FavoriteItemRepository;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.PaymentRepository;
import com.project.skin_me.repository.PopularProductRepository;
import com.project.skin_me.repository.ProductFeedbackRepository;
import com.project.skin_me.repository.ProductRepository;
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
import com.project.skin_me.service.feedback.IProductFeedbackService;
import com.project.skin_me.service.user.IUserService;
import com.project.skin_me.service.payment.IBakongKhqrService;
import com.project.skin_me.model.ChatAi;
import com.project.skin_me.model.KhqrBankAccount;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PageController {

    /** Number of items per page; only this many are loaded from DB per request. */
    private static final int PAGE_SIZE = 25;

    private static final int AUDIT_LOG_PAGE_SIZE = 20;

    private final ICategoryService categoryService;
    private final IBrandService brandService;
    private final IProductService productService;
    private final IImageService imageService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAiRepository chatAiRepository;
    private final IOrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final IUserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActivityRepository activityRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final PopularProductRepository popularProductRepository;
    private final ObjectMapper objectMapper;
    private final IBakongKhqrService bakongKhqrService;
    private final IProductFeedbackService productFeedbackService;
    private final ProductFeedbackRepository productFeedbackRepository;

    @org.springframework.beans.factory.annotation.Value("${stripe.public.key}")
    private String stripePublicKey;

    @org.springframework.beans.factory.annotation.Value("${payment.khqr.usd-to-khr-rate:4100}")
    private int khqrUsdToKhrRate;

    @org.springframework.beans.factory.annotation.Value("${aba.khqr.merchant.name:}")
    private String abaKhqrMerchantName;

    @org.springframework.beans.factory.annotation.Value("${aba.khqr.merchant.account:}")
    private String abaKhqrMerchantAccount;

    @org.springframework.beans.factory.annotation.Value("${aba.khqr.merchant.city:}")
    private String abaKhqrMerchantCity;

    @org.springframework.beans.factory.annotation.Value("${aba.khqr.merchant.category.code:}")
    private String abaKhqrCategoryCode;

    @org.springframework.beans.factory.annotation.Value("${khqr.merchant.name:}")
    private String genericKhqrMerchantName;

    @org.springframework.beans.factory.annotation.Value("${khqr.merchant.account:}")
    private String genericKhqrMerchantAccount;

    @org.springframework.beans.factory.annotation.Value("${khqr.merchant.city:}")
    private String genericKhqrMerchantCity;

    @org.springframework.beans.factory.annotation.Value("${khqr.merchant.category.code:}")
    private String genericKhqrCategoryCode;

    @org.springframework.beans.factory.annotation.Value("${app.dashboard.checkout-hint:QR defaults use aba.khqr.* (ABA) and khqr.merchant.* (any KHQR bank). Change in application.properties or environment.}")
    private String dashboardCheckoutHint;

    @org.springframework.beans.factory.annotation.Value("${app.dashboard.khqr.section-title:Checkout & QR merchants}")
    private String dashboardKhqrSectionTitle;

    @org.springframework.beans.factory.annotation.Value("${app.dashboard.khqr.aba-label:ABA Mobile}")
    private String dashboardKhqrAbaLabel;

    @org.springframework.beans.factory.annotation.Value("${app.dashboard.khqr.generic-label:KHQR (any bank)}")
    private String dashboardKhqrGenericLabel;

    @GetMapping("/login-page")
    public String loginPage(Model model,
            HttpServletRequest request,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String logout) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        // Only redirect to dashboard if authenticated and has ROLE_ADMIN
        if (auth != null && auth.isAuthenticated() &&
                !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (isAdmin) {
                return "redirect:/dashboard";
            }
            // Authenticated but not admin: show error and stay on login (they must sign in
            // as admin)
            model.addAttribute("error", "Invalid credentials. Only administrators can access the dashboard.");
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
        } else if ("access_denied".equals(error)) {
            model.addAttribute("error", "Invalid credentials. Only administrators can access the dashboard.");
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
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String dashboard(Model model) {
        try {
            // Use counts and sums instead of loading full tables (fast render)
            long totalProducts = productRepository.count();
            long totalOrders = orderRepository.count();
            long totalUsers = userRepository.count();
            java.math.BigDecimal sumResult = paymentRepository.sumAllAmounts();
            double totalRevenue = sumResult != null ? sumResult.doubleValue() : 0;

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
            java.time.LocalDateTime weekStart = now.minusDays(7);
            java.time.LocalDateTime monthStart = now.minusDays(30);

            long newUsersToday = userRepository.countByRegistrationDateAfter(todayStart);
            long newUsersThisWeek = userRepository.countByRegistrationDateAfter(weekStart);
            long newUsersThisMonth = userRepository.countByRegistrationDateAfter(monthStart);

            model.addAttribute("totalProducts", totalProducts);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("totalUsers", totalUsers);
            model.addAttribute("totalRevenue", totalRevenue);
            model.addAttribute("newUsersToday", newUsersToday);
            model.addAttribute("newUsersThisWeek", newUsersThisWeek);
            model.addAttribute("newUsersThisMonth", newUsersThisMonth);

            // Dashboard widgets: last 5 orders and 5 payments for card links
            Pageable top5 = PageRequest.of(0, 5, Sort.by("orderId").descending());
            model.addAttribute("recentOrders", orderService.getAllUserOrders(top5).getContent());
            Pageable top5Payments = PageRequest.of(0, 5, Sort.by("id").descending());
            model.addAttribute("recentPayments", paymentRepository.findAllWithOrderAndUser(top5Payments).getContent());

            // Recent favorites and popular: limit to 5 in memory (tables usually small)
            List<FavoriteItem> recentFavorites = favoriteItemRepository.findRecentFavoritesWithRelations();
            model.addAttribute("recentFavorites", recentFavorites.stream().limit(5).toList());
            List<PopularProduct> popularProducts = popularProductRepository.findTopPopularProductsWithRelations();
            model.addAttribute("popularProducts", popularProducts.stream().limit(5).toList());

            long totalFeedback = productFeedbackRepository.count();
            Pageable feedbackTop = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            model.addAttribute("totalFeedback", totalFeedback);
            model.addAttribute("dashboardFeedbackList",
                    productFeedbackService.listAllForAdmin(feedbackTop).getContent());

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
            model.addAttribute("recentOrders", List.<OrderDto>of());
            model.addAttribute("recentPayments", List.<Payment>of());
            model.addAttribute("totalFeedback", 0L);
            model.addAttribute("dashboardFeedbackList", List.<ProductFeedbackDto>of());
            model.addAttribute("error", "Failed to load stats: " + e.getMessage());
        }
        addDashboardCheckoutConfigAttributes(model);
        model.addAttribute("pageTitle", "Admin Dashboard");
        return "dashboard";
    }

    /** Values from application.properties / env for dashboard KHQR & checkout summary. */
    private void addDashboardCheckoutConfigAttributes(Model model) {
        model.addAttribute("abaKhqrMerchantName", abaKhqrMerchantName);
        model.addAttribute("abaKhqrMerchantAccount", abaKhqrMerchantAccount);
        model.addAttribute("abaKhqrMerchantCity", abaKhqrMerchantCity);
        model.addAttribute("abaKhqrCategoryCode", abaKhqrCategoryCode);
        model.addAttribute("genericKhqrMerchantName", genericKhqrMerchantName);
        model.addAttribute("genericKhqrMerchantAccount", genericKhqrMerchantAccount);
        model.addAttribute("genericKhqrMerchantCity", genericKhqrMerchantCity);
        model.addAttribute("genericKhqrCategoryCode", genericKhqrCategoryCode);
        model.addAttribute("khqrUsdToKhrRate", khqrUsdToKhrRate);
        model.addAttribute("dashboardCheckoutHint", dashboardCheckoutHint);
        model.addAttribute("dashboardKhqrSectionTitle", dashboardKhqrSectionTitle);
        model.addAttribute("dashboardKhqrAbaLabel", dashboardKhqrAbaLabel);
        model.addAttribute("dashboardKhqrGenericLabel", dashboardKhqrGenericLabel);
    }

    /**
     * Switch language for dashboard; LocaleChangeInterceptor sets cookie from
     * ?lang=. Redirects back to current page.
     */
    @GetMapping("/views/set-lang")
    public String setLang(@RequestParam String lang, HttpServletRequest request) {
        String redirectTo = "/dashboard";
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                java.net.URL u = new java.net.URL(referer);
                String path = u.getPath();
                if (path != null && !path.isBlank())
                    redirectTo = path;
            } catch (Exception ignored) {
            }
        }
        return "redirect:" + redirectTo;
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

    /** Allowed page sizes for product table. */
    private static final int[] PRODUCT_PAGE_SIZES = { 10, 20, 50 };

    @GetMapping("/views/products")
    @PreAuthorize("hasRole('ADMIN')")
    public String productsListPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            Model model,
            org.springframework.web.context.request.WebRequest webRequest) {
        if (size == null && !webRequest.getParameterMap().containsKey("size")) {
            return "redirect:/views/products?page=0&size=10";
        }
        int pageSize = normalizeProductPageSize(size != null ? size : 10);
        try {
            Pageable pageable = PageRequest.of(page, pageSize, Sort.by("id"));
            var productPage = productService.getAllProducts(pageable);
            int totalPages = productPage.getTotalPages();
            long totalItems = productPage.getTotalElements();
            int safePage = totalPages > 0 ? Math.min(page, totalPages - 1) : 0;
            if (safePage != page) {
                pageable = PageRequest.of(safePage, pageSize, Sort.by("id"));
                productPage = productService.getAllProducts(pageable);
            }
            List<Product> products = productPage.getContent();
            List<Category> categories = categoryService.getAllCategories();
            page = safePage;
            model.addAttribute("products", products);
            model.addAttribute("categories", categories);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("totalItems", totalItems);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("pageSizeOptions", PRODUCT_PAGE_SIZES);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load products: " + e.getMessage());
            model.addAttribute("products", List.<Product>of());
            model.addAttribute("categories", List.<Category>of());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalItems", 0);
            model.addAttribute("pageSize", 10);
            model.addAttribute("pageSizeOptions", PRODUCT_PAGE_SIZES);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        }
        model.addAttribute("pageTitle", "Products Management");
        return "products";
    }

    private static int normalizeProductPageSize(int size) {
        for (int allowed : PRODUCT_PAGE_SIZES) {
            if (allowed == size)
                return size;
        }
        return 10;
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
            model.addAttribute("favoriteCount", productService.countFavoriteUsersByProductId(productId));
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
            model.addAttribute("favoriteCount", productService.countFavoriteUsersByProductId(productId));
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
    private static final Map<OrderStatus, String> ORDER_STATUS_COLORS = new LinkedHashMap<>() {
        {
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
        }
    };

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
            if (totalRevenue == null)
                totalRevenue = BigDecimal.ZERO;
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
                    sum = row[2] instanceof BigDecimal ? (BigDecimal) row[2]
                            : BigDecimal.valueOf(((Number) row[2]).doubleValue());
                }
                String key = y + "-" + m;
                monthToRevenue.put(key, sum);
            }
            List<SalesMonthDto> salesByMonth = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                LocalDate d = since.plusMonths(i);
                String key = d.getYear() + "-" + d.getMonthValue();
                String label = d.getMonth().name().substring(0, 1) + d.getMonth().name().substring(1).toLowerCase()
                        + " " + d.getYear();
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
            Payment payment = paymentRepository.findByIdWithOrderAndUser(paymentId).orElse(null);
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
    @PreAuthorize("isAuthenticated()")
    public String getMyPaymentsView(Authentication authentication, Model model) {
        try {
            User user = userService.getAuthenticatedUser();
            if (user == null) {
                model.addAttribute("payments", List.<Payment>of());
                model.addAttribute("totalPayments", 0);
                model.addAttribute("totalPaymentAmount", 0.0);
                model.addAttribute("currentPage", 0);
                model.addAttribute("totalPages", 0);
                model.addAttribute("hasNext", false);
                model.addAttribute("hasPrev", false);
                model.addAttribute("pageTitle", "User Payment");
                model.addAttribute("pageSubtitle", "Payment Record");
                return "my-payments";
            }
            List<Payment> payments = paymentRepository.findByOrderUserIdWithOrderAndUser(user.getId());
            java.math.BigDecimal sum = paymentRepository.sumAmountsByUserId(user.getId());
            double totalPaymentAmount = sum != null ? sum.doubleValue() : 0.0;
            int total = payments != null ? payments.size() : 0;
            model.addAttribute("payments", payments != null ? payments : List.<Payment>of());
            model.addAttribute("pageTitle", "User Payment");
            model.addAttribute("pageSubtitle", "Payment Record");
            model.addAttribute("totalPayments", total);
            model.addAttribute("totalPaymentAmount", totalPaymentAmount);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", total > 0 ? 1 : 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load your payments: " + e.getMessage());
            model.addAttribute("payments", List.<Payment>of());
            model.addAttribute("totalPayments", 0);
            model.addAttribute("totalPaymentAmount", 0.0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
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
            model.addAttribute("khqrUsdToKhrRate", khqrUsdToKhrRate);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load order: " + e.getMessage());
            model.addAttribute("pageTitle", "Checkout Error");
            model.addAttribute("khqrUsdToKhrRate", khqrUsdToKhrRate);
        }
        return "checkout";
    }

    @GetMapping("/views/payments")
    @PreAuthorize("isAuthenticated()")
    public String paymentsListPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").descending());
            var paymentPage = paymentRepository.findAllWithOrderAndUser(pageable);
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
            Authentication authentication, Model model) {
        try {
            User user = userService.getAuthenticatedUser();
            if (user == null) {
                model.addAttribute("payments", List.<Payment>of());
                model.addAttribute("totalPayments", 0);
                model.addAttribute("totalPaymentAmount", 0.0);
                model.addAttribute("currentPage", 0);
                model.addAttribute("totalPages", 0);
                model.addAttribute("hasNext", false);
                model.addAttribute("hasPrev", false);
                model.addAttribute("pageTitle", "User Payment");
                model.addAttribute("pageSubtitle", "Payment Record");
                return "my-payments";
            }
            // Load all user payments in one query (same as Payment Record pattern) then
            // paginate in memory
            // to avoid JOIN FETCH + Pageable issues and ensure data always displays
            List<Payment> allPayments = paymentRepository.findByOrderUserIdWithOrderAndUser(user.getId());
            int total = allPayments != null ? allPayments.size() : 0;
            int totalPages = total > 0 ? (int) Math.ceil((double) total / PAGE_SIZE) : 0;
            int from = Math.min(page * PAGE_SIZE, total);
            int to = Math.min(from + PAGE_SIZE, total);
            List<Payment> payments = (allPayments != null && from < allPayments.size())
                    ? allPayments.subList(from, to)
                    : List.<Payment>of();

            java.math.BigDecimal sum = paymentRepository.sumAmountsByUserId(user.getId());
            double totalPaymentAmount = sum != null ? sum.doubleValue() : 0.0;

            model.addAttribute("payments", payments);
            model.addAttribute("totalPayments", (long) total);
            model.addAttribute("totalPaymentAmount", totalPaymentAmount);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", page < totalPages - 1);
            model.addAttribute("hasPrev", page > 0);
            model.addAttribute("pageTitle", "User Payment");
            model.addAttribute("pageSubtitle", "Payment Record");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load your payments: " + e.getMessage());
            model.addAttribute("payments", List.<Payment>of());
            model.addAttribute("totalPayments", 0);
            model.addAttribute("totalPaymentAmount", 0.0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
            model.addAttribute("pageTitle", "User Payment");
            model.addAttribute("pageSubtitle", "Payment Record");
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

    /** Admin-only: view all chat tables - chat_messages and chat_ai. */
    @GetMapping("/views/chat-activity")
    @PreAuthorize("hasRole('ADMIN')")
    public String chatActivityPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String session,
            @RequestParam(defaultValue = "0") int pageMsg,
            @RequestParam(defaultValue = "0") int pageAi,
            Model model) {
        try {
            // --- chat_messages ---
            Pageable pageableMsg = PageRequest.of(pageMsg, PAGE_SIZE, Sort.by("timestamp").descending());
            org.springframework.data.domain.Page<com.project.skin_me.model.ChatMessage> msgPage;
            if (userId != null) {
                msgPage = chatMessageRepository.findByUserIdWithUserOrderByTimestampDesc(userId, pageableMsg);
            } else {
                msgPage = chatMessageRepository.findAllWithUserOrderByTimestampDesc(pageableMsg);
            }
            List<com.project.skin_me.model.ChatMessage> chatMessages = msgPage.getContent();
            int totalPagesMsg = msgPage.getTotalPages();
            long totalItemsMsg = msgPage.getTotalElements();

            // --- chat_ai ---
            Pageable pageableAi = PageRequest.of(pageAi, PAGE_SIZE, Sort.by("timestamp").descending());
            org.springframework.data.domain.Page<ChatAi> chatPage;
            if (session != null && !session.isBlank()) {
                chatPage = chatAiRepository.findBySessionOrderByTimestampDesc(session.trim(), pageableAi);
            } else {
                chatPage = chatAiRepository.findAllByOrderByTimestampDesc(pageableAi);
            }
            List<ChatAi> chatAiList = chatPage.getContent();
            int totalPagesAi = chatPage.getTotalPages();
            long totalItemsAi = chatPage.getTotalElements();
            List<String> distinctSessions = chatAiRepository.findDistinctSessions();

            String encSession = (session != null && !session.isBlank())
                    ? java.net.URLEncoder.encode(session.trim(), java.nio.charset.StandardCharsets.UTF_8).replace("+",
                            "%20")
                    : null;

            model.addAttribute("chatMessages", chatMessages);
            model.addAttribute("totalPagesMsg", totalPagesMsg);
            model.addAttribute("currentPageMsg", pageMsg);
            model.addAttribute("totalItemsMsg", totalItemsMsg);
            model.addAttribute("hasNextMsg", pageMsg < totalPagesMsg - 1);
            model.addAttribute("hasPrevMsg", pageMsg > 0);
            model.addAttribute("chatAiList", chatAiList);
            model.addAttribute("distinctSessions", distinctSessions != null ? distinctSessions : List.<String>of());
            model.addAttribute("selectedSession", session != null && !session.isBlank() ? session.trim() : null);
            model.addAttribute("totalPagesAi", totalPagesAi);
            model.addAttribute("currentPageAi", pageAi);
            model.addAttribute("totalItemsAi", totalItemsAi);
            model.addAttribute("hasNextAi", pageAi < totalPagesAi - 1);
            model.addAttribute("hasPrevAi", pageAi > 0);
            model.addAttribute("allUsers", userRepository.findAll());
            model.addAttribute("selectedUserId", userId);

            StringBuilder base = new StringBuilder("/views/chat-activity?");
            if (userId != null)
                base.append("userId=").append(userId).append("&");
            if (encSession != null)
                base.append("session=").append(encSession).append("&");
            model.addAttribute("paginationBaseMsg", base.toString() + "pageAi=" + pageAi + "&");
            model.addAttribute("paginationBaseAi", base.toString() + "pageMsg=" + pageMsg + "&");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load chat activity: " + e.getMessage());
            model.addAttribute("chatMessages", List.<com.project.skin_me.model.ChatMessage>of());
            model.addAttribute("chatAiList", List.<ChatAi>of());
            model.addAttribute("distinctSessions", List.<String>of());
            model.addAttribute("allUsers", List.<User>of());
            model.addAttribute("selectedUserId", null);
            model.addAttribute("selectedSession", null);
            model.addAttribute("totalPagesMsg", 0);
            model.addAttribute("currentPageMsg", 0);
            model.addAttribute("totalItemsMsg", 0L);
            model.addAttribute("hasNextMsg", false);
            model.addAttribute("hasPrevMsg", false);
            model.addAttribute("totalPagesAi", 0);
            model.addAttribute("currentPageAi", 0);
            model.addAttribute("totalItemsAi", 0L);
            model.addAttribute("hasNextAi", false);
            model.addAttribute("hasPrevAi", false);
            model.addAttribute("paginationBaseMsg", "/views/chat-activity?pageAi=0&");
            model.addAttribute("paginationBaseAi", "/views/chat-activity?pageMsg=0&");
        }
        model.addAttribute("pageTitle", "Chat Activity");
        return "chat-activity";
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

    @GetMapping("/views/user-feedback")
    @PreAuthorize("hasRole('ADMIN')")
    public String userFeedbackPage(@RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<ProductFeedbackDto> feedbackPage = productFeedbackService.listAllForAdmin(pageable);
            long totalInDb = productFeedbackRepository.count();
            long visibleCount = productFeedbackRepository.countByVisibleOnFrontendTrue();
            model.addAttribute("feedbackList", feedbackPage.getContent());
            model.addAttribute("currentPage", feedbackPage.getNumber());
            model.addAttribute("totalPages", feedbackPage.getTotalPages());
            model.addAttribute("totalElements", feedbackPage.getTotalElements());
            model.addAttribute("totalFeedbackCount", totalInDb);
            model.addAttribute("visibleOnStorefrontCount", visibleCount);
            model.addAttribute("hasNext", feedbackPage.hasNext());
            model.addAttribute("hasPrev", feedbackPage.hasPrevious());
            model.addAttribute("pageTitle", "User Feedback");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load feedback: " + e.getMessage());
            model.addAttribute("feedbackList", List.<ProductFeedbackDto>of());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalElements", 0L);
            model.addAttribute("totalFeedbackCount", 0L);
            model.addAttribute("visibleOnStorefrontCount", 0L);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
            model.addAttribute("pageTitle", "User Feedback");
        }
        return "user-feedback";
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
            model.addAttribute("currentUserId", userService.getAuthenticatedUser().getId());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load users: " + e.getMessage());
            model.addAttribute("users", List.<User>of());
            model.addAttribute("allRoles", List.<Role>of());
            model.addAttribute("totalUsers", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
            model.addAttribute("currentUserId", null);
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
        } catch (IllegalStateException e) {
            return "redirect:/views/users?error=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
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

    // KHQR Bank Accounts (admin)
    @GetMapping("/views/khqr-accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public String khqrAccountsListPage(Model model) {
        try {
            List<KhqrBankAccount> accounts = bakongKhqrService.findAll();
            model.addAttribute("accounts", accounts);
            model.addAttribute("pageTitle", "KHQR Bank Accounts");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load bank accounts: " + e.getMessage());
            model.addAttribute("accounts", List.<KhqrBankAccount>of());
            model.addAttribute("pageTitle", "KHQR Bank Accounts");
        }
        return "khqr-accounts";
    }

    @GetMapping("/views/khqr-accounts/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String khqrAccountCreatePage(Model model) {
        model.addAttribute("account", null);
        model.addAttribute("pageTitle", "Add KHQR Bank Account");
        return "khqr-account-form";
    }

    @GetMapping("/views/khqr-accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String khqrAccountDetailPage(@PathVariable Long id, Model model) {
        try {
            KhqrBankAccount account = bakongKhqrService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));
            model.addAttribute("account", account);
            model.addAttribute("pageTitle", "Bank Account Details");
            return "khqr-account-details";
        } catch (Exception e) {
            return "redirect:/views/khqr-accounts?error=Account not found";
        }
    }

    @PostMapping("/views/khqr-accounts/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String khqrAccountCreate(
            @RequestParam String gateway,
            @RequestParam String account,
            @RequestParam String merchantName,
            @RequestParam String city,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String testAccountUsd,
            @RequestParam(required = false) String testAccountKhr,
            @RequestParam(defaultValue = "false") boolean useTestWhenEmpty,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(required = false) String bakongToken,
            @RequestParam(required = false) String telegramChatId,
            @RequestParam(required = false) String paywayMerchantId,
            @RequestParam(required = false) String paywayPublicKey,
            @RequestParam(required = false) String paywayApiUrl,
            Model model) {
        try {
            KhqrBankAccount entity = KhqrBankAccount.builder()
                    .gateway(gateway != null ? gateway.trim().toLowerCase() : "khqr")
                    .account(account != null ? account.trim() : null)
                    .merchantName(merchantName != null ? merchantName.trim() : "")
                    .city(city != null ? city.trim() : "")
                    .categoryCode(categoryCode != null && !categoryCode.isBlank() ? categoryCode.trim() : "5999")
                    .testAccountUsd(testAccountUsd != null && !testAccountUsd.isBlank() ? testAccountUsd.trim() : null)
                    .testAccountKhr(testAccountKhr != null && !testAccountKhr.isBlank() ? testAccountKhr.trim() : null)
                    .useTestWhenEmpty(useTestWhenEmpty)
                    .active(active)
                    .displayOrder(displayOrder)
                    .bakongToken(bakongToken != null && !bakongToken.isBlank() ? bakongToken.trim() : null)
                    .telegramChatId(telegramChatId != null && !telegramChatId.isBlank() ? telegramChatId.trim() : null)
                    .paywayMerchantId(
                            paywayMerchantId != null && !paywayMerchantId.isBlank() ? paywayMerchantId.trim() : null)
                    .paywayPublicKey(
                            paywayPublicKey != null && !paywayPublicKey.isBlank() ? paywayPublicKey.trim() : null)
                    .paywayApiUrl(paywayApiUrl != null && !paywayApiUrl.isBlank() ? paywayApiUrl.trim() : null)
                    .build();
            bakongKhqrService.create(entity);
            return "redirect:/views/khqr-accounts?success=Bank account created successfully";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create: " + e.getMessage());
            model.addAttribute("account", null);
            model.addAttribute("pageTitle", "Add KHQR Bank Account");
            return "khqr-account-form";
        }
    }

    @GetMapping("/views/khqr-accounts/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String khqrAccountEditPage(@PathVariable Long id, Model model) {
        try {
            KhqrBankAccount account = bakongKhqrService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));
            model.addAttribute("account", account);
            model.addAttribute("pageTitle", "Edit KHQR Bank Account");
            return "khqr-account-form";
        } catch (Exception e) {
            return "redirect:/views/khqr-accounts?error=Account not found";
        }
    }

    @PostMapping("/views/khqr-accounts/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String khqrAccountUpdate(
            @PathVariable Long id,
            @RequestParam String gateway,
            @RequestParam String account,
            @RequestParam String merchantName,
            @RequestParam String city,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String testAccountUsd,
            @RequestParam(required = false) String testAccountKhr,
            @RequestParam(defaultValue = "false") boolean useTestWhenEmpty,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(required = false) String bakongToken,
            @RequestParam(required = false) String telegramChatId,
            @RequestParam(required = false) String paywayMerchantId,
            @RequestParam(required = false) String paywayPublicKey,
            @RequestParam(required = false) String paywayApiUrl,
            Model model) {
        try {
            KhqrBankAccount entity = KhqrBankAccount.builder()
                    .gateway(gateway != null ? gateway.trim().toLowerCase() : "khqr")
                    .account(account != null ? account.trim() : null)
                    .merchantName(merchantName != null ? merchantName.trim() : "")
                    .city(city != null ? city.trim() : "")
                    .categoryCode(categoryCode != null && !categoryCode.isBlank() ? categoryCode.trim() : "5999")
                    .testAccountUsd(testAccountUsd != null && !testAccountUsd.isBlank() ? testAccountUsd.trim() : null)
                    .testAccountKhr(testAccountKhr != null && !testAccountKhr.isBlank() ? testAccountKhr.trim() : null)
                    .useTestWhenEmpty(useTestWhenEmpty)
                    .active(active)
                    .displayOrder(displayOrder)
                    .bakongToken(bakongToken != null && !bakongToken.isBlank() ? bakongToken.trim() : null)
                    .telegramChatId(telegramChatId != null && !telegramChatId.isBlank() ? telegramChatId.trim() : null)
                    .paywayMerchantId(
                            paywayMerchantId != null && !paywayMerchantId.isBlank() ? paywayMerchantId.trim() : null)
                    .paywayPublicKey(
                            paywayPublicKey != null && !paywayPublicKey.isBlank() ? paywayPublicKey.trim() : null)
                    .paywayApiUrl(paywayApiUrl != null && !paywayApiUrl.isBlank() ? paywayApiUrl.trim() : null)
                    .build();
            bakongKhqrService.update(id, entity);
            return "redirect:/views/khqr-accounts?success=Bank account updated successfully";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to update: " + e.getMessage());
            try {
                model.addAttribute("account", bakongKhqrService.findById(id).orElse(null));
            } catch (Exception ignored) {
            }
            model.addAttribute("pageTitle", "Edit KHQR Bank Account");
            return "khqr-account-form";
        }
    }

    @PostMapping("/views/khqr-accounts/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String khqrAccountDelete(@PathVariable Long id) {
        try {
            bakongKhqrService.deleteById(id);
            return "redirect:/views/khqr-accounts?success=Bank account deleted successfully";
        } catch (Exception e) {
            return "redirect:/views/khqr-accounts?error=Failed to delete: " + e.getMessage();
        }
    }

    // Audit Log Management
    @GetMapping("/views/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public String auditLogsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String activityType,
            Model model) {
        try {
            Pageable pageable = PageRequest.of(page, AUDIT_LOG_PAGE_SIZE,
                    Sort.by(Sort.Direction.DESC, "timestamp"));
            Page<Activity> activityPage;

            if (userId != null) {
                activityPage = activityRepository.findByUser_IdOrderByTimestampDesc(userId, pageable);
            } else if (activityType != null && !activityType.isEmpty()) {
                try {
                    com.project.skin_me.enums.ActivityType type = com.project.skin_me.enums.ActivityType
                            .valueOf(activityType.toUpperCase());
                    activityPage = activityRepository.findByActivityTypeOrderByTimestampDesc(type, pageable);
                } catch (IllegalArgumentException e) {
                    activityPage = activityRepository.findAllByOrderByTimestampDesc(pageable);
                }
            } else {
                activityPage = activityRepository.findAllByOrderByTimestampDesc(pageable);
            }

            model.addAttribute("activities", activityPage.getContent());
            model.addAttribute("currentPage", activityPage.getNumber());
            model.addAttribute("totalPages", activityPage.getTotalPages());
            model.addAttribute("totalActivities", activityPage.getTotalElements());
            model.addAttribute("hasNext", activityPage.hasNext());
            model.addAttribute("hasPrev", activityPage.hasPrevious());
            model.addAttribute("auditPageSize", AUDIT_LOG_PAGE_SIZE);
            model.addAttribute("pageTitle", "Audit Log Management");
            model.addAttribute("filterUserId", userId);
            model.addAttribute("filterActivityType",
                    activityType != null && !activityType.isBlank() ? activityType : null);

            StringBuilder paginationBase = new StringBuilder("/views/audit-logs");
            boolean firstQuery = true;
            if (userId != null) {
                paginationBase.append(firstQuery ? "?" : "&").append("userId=").append(userId);
                firstQuery = false;
            }
            if (activityType != null && !activityType.isEmpty()) {
                paginationBase.append(firstQuery ? "?" : "&").append("activityType=").append(activityType);
            }
            model.addAttribute("auditLogsPaginationBase", paginationBase.toString());

            model.addAttribute("allUsers", userRepository.findAll());

        } catch (Exception e) {
            model.addAttribute("error", "Failed to load audit logs: " + e.getMessage());
            model.addAttribute("activities", List.<Activity>of());
            model.addAttribute("totalActivities", 0L);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrev", false);
            model.addAttribute("auditPageSize", AUDIT_LOG_PAGE_SIZE);
            model.addAttribute("auditLogsPaginationBase", "/views/audit-logs");
            model.addAttribute("filterUserId", null);
            model.addAttribute("filterActivityType", null);
            try {
                model.addAttribute("allUsers", userRepository.findAll());
            } catch (Exception ignored) {
                model.addAttribute("allUsers", List.<User>of());
            }
        }
        return "audit-logs";
    }

}
