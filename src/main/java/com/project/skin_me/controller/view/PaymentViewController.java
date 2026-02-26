package com.project.skin_me.controller.view;

import com.project.skin_me.model.Payment;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.PaymentRepository;
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
@RequestMapping("/view/payments")
public class PaymentViewController {

    private final PaymentRepository paymentRepository;
    private final IUserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String getAllPayments(Model model) {
        try {
            List<Payment> payments = paymentRepository.findAll();
            model.addAttribute("payments", payments);
            model.addAttribute("pageTitle", "Payments Record");
            model.addAttribute("totalPayments", payments.size());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load payments: " + e.getMessage());
        }
        return "payments";
    }

    @GetMapping("/my-payments")
    @PreAuthorize("hasRole('USER')")
    public String getMyPayments(Authentication authentication, Model model) {
        try {
            User user = userService.getAuthenticatedUser();
            List<Payment> payments = paymentRepository.findByOrderUserId(user.getId());

            model.addAttribute("payments", payments);
            model.addAttribute("pageTitle", "My Payments");
            model.addAttribute("totalPayments", payments.size());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load your payments: " + e.getMessage());
        }
        return "my-payments";
    }

    @GetMapping("/{paymentId}")
    public String getPaymentById(@PathVariable Long paymentId, Model model) {
        try {
            Payment payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment == null) {
                model.addAttribute("error", "Payment not found");
                return "payment-details";
            }
            model.addAttribute("payment", payment);
            model.addAttribute("pageTitle", "Payment Details #" + paymentId);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load payment: " + e.getMessage());
        }
        return "payment-details";
    }
}
