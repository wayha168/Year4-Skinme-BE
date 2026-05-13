package com.project.skin_me.unit.service.order;

import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Cart;
import com.project.skin_me.model.CartItem;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.OrderItem;
import com.project.skin_me.model.Payment;
import com.project.skin_me.model.Product;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.ActivityRepository;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.PaymentRepository;
import com.project.skin_me.repository.ProductRepository;
import com.project.skin_me.service.cart.ICartService;
import com.project.skin_me.service.notification.NotificationService;
import com.project.skin_me.service.order.DeliveryFeeService;
import com.project.skin_me.service.order.OrderService;
import com.project.skin_me.service.payment.IBakongKhqrService;
import com.project.skin_me.service.popularProduct.IPopularProductService;
import com.project.skin_me.service.telegram.TelegramNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ICartService cartService;
    @Mock
    private IPopularProductService popularProductService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TelegramNotificationService telegramNotificationService;
    @Mock
    private IBakongKhqrService bakongKhqrService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private DeliveryFeeService deliveryFeeService;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Cart cart;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail("buyer@example.com");

        cart = new Cart();
        cart.setId(100L);
        cart.setUser(user);
    }

    @Test
    void placeOrderItem_buildsTotalsAndPersistsOrder() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Cleanser");
        product.setInventory(10);

        CartItem line = new CartItem();
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("15.00"));
        line.setProduct(product);
        cart.setItems(new HashSet<>(Set.of(line)));

        when(cartService.getCartByUserId(7L)).thenReturn(cart);
        when(deliveryFeeService.computeDeliveryFee(new BigDecimal("30.00"))).thenReturn(new BigDecimal("2.50"));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setOrderId(500L);
            return o;
        });

        when(bakongKhqrService.getFirstActiveTelegramChatId()).thenReturn(Optional.empty());

        Order saved = orderService.placeOrderItem(7L);

        assertThat(saved.getOrderId()).isEqualTo(500L);
        assertThat(saved.getItemsSubtotalAmount()).isEqualByComparingTo("30.00");
        assertThat(saved.getDeliveryFeeAmount()).isEqualByComparingTo("2.50");
        assertThat(saved.getOrderTotalAmount()).isEqualByComparingTo("32.50");
        assertThat(saved.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getOrderItems()).hasSize(1);

        verify(orderRepository).save(any(Order.class));
        verify(notificationService).notifyUserWithAction(eq("7"), anyString(), anyString(), eq("ORDER"), anyString());
    }

    @Test
    void placeOrderItem_throwsWhenInventoryInsufficient() {
        Product product = new Product();
        product.setName("Serum");
        product.setInventory(1);

        CartItem line = new CartItem();
        line.setQuantity(3);
        line.setUnitPrice(BigDecimal.TEN);
        line.setProduct(product);
        cart.setItems(new HashSet<>(Set.of(line)));

        when(cartService.getCartByUserId(7L)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.placeOrderItem(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock")
                .hasMessageContaining("Serum");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrder_throwsWhenMissing() {
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void confirmOrderPayment_returnsEarlyWhenOrderAlreadyPaid() {
        Order input = new Order();
        input.setOrderId(1L);

        Order managed = new Order();
        managed.setOrderId(1L);
        managed.setOrderStatus(OrderStatus.PAID);
        managed.setUser(user);

        when(orderRepository.findByIdWithOrderItemsAndProducts(1L)).thenReturn(Optional.of(managed));

        orderService.confirmOrderPayment(input);

        verify(paymentRepository, never()).save(any());
        verify(productRepository, never()).save(any());
        verify(popularProductService, never()).saveFromOrder(any());
    }

    @Test
    void confirmOrderPayment_throwsWhenPaymentRowMissing() {
        Order input = new Order();
        input.setOrderId(2L);

        Product product = new Product();
        product.setId(9L);
        product.setName("Toner");
        product.setInventory(5);

        OrderItem oi = new OrderItem(null, product, 1, new BigDecimal("12.00"));
        Order managed = new Order();
        managed.setOrderId(2L);
        managed.setOrderStatus(OrderStatus.PENDING);
        managed.setUser(user);
        managed.setStripeSessionId(null);
        managed.setOrderItems(new HashSet<>(Set.of(oi)));

        when(orderRepository.findByIdWithOrderItemsAndProducts(2L)).thenReturn(Optional.of(managed));
        when(paymentRepository.findByOrder(managed)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrderPayment(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment not found");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void confirmOrderPayment_updatesInventoryPaymentAndOrderStatus() {
        Order input = new Order();
        input.setOrderId(3L);

        Product product = new Product();
        product.setId(9L);
        product.setName("Toner");
        product.setInventory(5);

        OrderItem oi = new OrderItem(null, product, 2, new BigDecimal("12.00"));
        Order managed = new Order();
        managed.setOrderId(3L);
        managed.setOrderStatus(OrderStatus.PENDING);
        managed.setUser(user);
        managed.setOrderTotalAmount(new BigDecimal("24.00"));
        managed.setStripeSessionId(null);
        managed.setOrderItems(new HashSet<>(Set.of(oi)));

        Payment payment = new Payment();
        payment.setId(88L);
        payment.setStatus(OrderStatus.PENDING);

        when(orderRepository.findByIdWithOrderItemsAndProducts(3L)).thenReturn(Optional.of(managed));
        when(paymentRepository.findByOrder(managed)).thenReturn(Optional.of(payment));
        when(cartService.getUserActiveCart(user)).thenReturn(Optional.empty());
        when(bakongKhqrService.getFirstActiveTelegramChatId()).thenReturn(Optional.empty());

        orderService.confirmOrderPayment(input);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getInventory()).isEqualTo(3);

        assertThat(payment.getStatus()).isEqualTo(OrderStatus.SUCCESS);

        verify(paymentRepository).save(payment);
        verify(popularProductService).saveFromOrder(managed);
        verify(orderRepository).save(managed);
        assertThat(managed.getOrderStatus()).isEqualTo(OrderStatus.PAID);
    }
}
