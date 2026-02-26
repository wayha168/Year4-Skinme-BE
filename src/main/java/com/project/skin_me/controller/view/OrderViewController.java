package com.project.skin_me.controller.view;

import com.project.skin_me.dto.OrderDto;
import com.project.skin_me.model.User;
import com.project.skin_me.service.order.IOrderService;
import com.project.skin_me.service.user.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

// @Controller - Disabled: Routes consolidated into PageController
@RequiredArgsConstructor
@RequestMapping("/view/orders")
public class OrderViewController {

    private final IOrderService orderService;
    private final IUserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String getAllOrders(Model model) {
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
            model.addAttribute("pendingOrders", pendingCount);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load orders: " + e.getMessage());
        }
        return "orders";
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('USER')")
    public String getMyOrders(Authentication authentication, Model model) {
        try {
            User user = userService.getAuthenticatedUser();
            List<OrderDto> orderDtos = orderService.getUserOrders(user.getId());

            model.addAttribute("orders", orderDtos);
            model.addAttribute("pageTitle", "My Orders");
            model.addAttribute("totalOrders", orderDtos.size());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load your orders: " + e.getMessage());
        }
        return "my-orders";
    }

    @GetMapping("/{orderId}")
    public String getOrderById(@PathVariable Long orderId, Model model) {
        try {
            OrderDto order = orderService.getOrder(orderId);
            model.addAttribute("order", order);
            model.addAttribute("pageTitle", "Order Details #" + orderId);
        } catch (Exception e) {
            model.addAttribute("error", "Order not found: " + e.getMessage());
        }
        return "order-details";
    }
}
