package com.project.skin_me.unit.service.cart;

import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Cart;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.CartItemRepository;
import com.project.skin_me.repository.CartRepository;
import com.project.skin_me.service.cart.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fast unit tests (no Spring context): mock repositories, assert service behavior — typical TDD style.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private CartService cartService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
    }

    @Nested
    @DisplayName("getCart")
    class GetCart {

        @Test
        void returnsCartWhenPresent() {
            Cart cart = new Cart();
            cart.setId(1L);
            when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

            assertThat(cartService.getCart(1L)).isSameAs(cart);
        }

        @ParameterizedTest
        @ValueSource(longs = { 0L, 99L, 1_000L })
        void throwsWhenMissing(long cartId) {
            when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.getCart(cartId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Cart not found");
        }
    }

    @Test
    void getTotalPrice_returnsCartTotal() {
        Cart cart = new Cart();
        cart.setId(3L);
        cart.setTotalAmount(new BigDecimal("19.99"));
        when(cartRepository.findById(3L)).thenReturn(Optional.of(cart));

        assertThat(cartService.getTotalPrice(3L)).isEqualByComparingTo("19.99");
    }

    @Test
    void getCartByUserId_throwsWhenNull() {
        when(cartRepository.findByUserIdWithRelations(7L)).thenReturn(null);

        assertThatThrownBy(() -> cartService.getCartByUserId(7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart not found for user");
    }

    @Test
    void getCartByUserId_returnsCart() {
        Cart cart = new Cart();
        cart.setId(2L);
        when(cartRepository.findByUserIdWithRelations(7L)).thenReturn(cart);

        assertThat(cartService.getCartByUserId(7L)).isSameAs(cart);
    }

    @Test
    void getUserActiveCart_delegatesToRepository() {
        Cart cart = new Cart();
        when(cartRepository.findByUserIdAndActiveWithRelations(42L, true)).thenReturn(Optional.of(cart));

        assertThat(cartService.getUserActiveCart(user)).contains(cart);
    }

    @Test
    void initializeNewCart_persistsActiveEmptyCartForUser() {
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });

        Cart result = cartService.initializeNewCart(user);

        verify(cartRepository).save(any(Cart.class));
        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.isActive()).isTrue();
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void removeCart_deletesItemsThenCart() {
        Cart cart = new Cart();
        cart.setId(5L);
        when(cartRepository.findById(5L)).thenReturn(Optional.of(cart));

        cartService.removeCart(5L);

        verify(cartItemRepository).deleteByCartId(eq(5L));
        verify(cartRepository).deleteById(5L);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void getAllCarts_returnsRepositoryList() {
        Cart a = new Cart();
        Cart b = new Cart();
        when(cartRepository.findAll()).thenReturn(List.of(a, b));

        assertThat(cartService.getAllCarts()).containsExactly(a, b);
    }
}
