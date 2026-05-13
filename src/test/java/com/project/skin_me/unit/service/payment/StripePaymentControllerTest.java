package com.project.skin_me.unit.service.payment;

import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.Payment;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.PaymentRepository;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.checkout.ICheckoutService;
import com.project.skin_me.service.notification.NotificationService;
import com.project.skin_me.service.order.IOrderService;
import com.project.skin_me.service.payment.IBakongKhqrService;
import com.project.skin_me.controller.api.PaymentController;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripePaymentControllerTest {

    @Mock
    private ICheckoutService checkoutService;
    @Mock
    private IOrderService orderService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private IBakongKhqrService bakongKhqrService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PaymentController paymentController;

    private Order order;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(7L);
        user.setEmail("buyer@example.com");

        order = new Order();
        order.setOrderId(99L);
        order.setUser(user);
        order.setOrderStatus(OrderStatus.PENDING);
    }

    @Test
    void createCheckoutSession_returns400WhenAmountBelowStripeMinimum() throws Exception {
        order.setOrderTotalAmount(new BigDecimal("0.40"));
        when(orderService.placeOrderItem(7L)).thenReturn(order);

        ResponseEntity<ApiResponse> response = paymentController.createCheckoutSession(7L, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("below Stripe minimum");
        verify(checkoutService, never()).createCheckoutSession(any(), any());
    }

    @Test
    void createCheckoutSession_createsStripeSessionAndPendingPayment() throws Exception {
        order.setOrderTotalAmount(new BigDecimal("10.00"));
        when(orderService.placeOrderItem(7L)).thenReturn(order);

        Session session = org.mockito.Mockito.mock(Session.class);
        when(session.getId()).thenReturn("cs_test_123");
        when(session.getUrl()).thenReturn("https://checkout.stripe.test/session");
        when(checkoutService.createCheckoutSession(99L, 1000L)).thenReturn(session);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.empty());

        Payment payment = new Payment();
        payment.setId(501L);
        payment.setOrder(order);
        payment.setStatus(OrderStatus.PENDING);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        ResponseEntity<ApiResponse> response = paymentController.createCheckoutSession(7L, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Stripe checkout session created");
        assertThat(order.getStripeSessionId()).isEqualTo("cs_test_123");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

        Object dataObj = response.getBody().getData();
        assertThat(dataObj).isInstanceOf(Map.class);
        Map<?, ?> data = (Map<?, ?>) dataObj;
        assertThat(data.get("sessionId")).isEqualTo("cs_test_123");
        assertThat(data.get("paymentMethod")).isEqualTo("CARD");
        assertThat(data.get("reusedSession")).isEqualTo(false);

        verify(checkoutService).createCheckoutSession(99L, 1000L);
        verify(orderService).updateOrder(eq(order));
        verify(paymentRepository).save(any(Payment.class));
    }
}
