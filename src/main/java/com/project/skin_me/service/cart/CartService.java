package com.project.skin_me.service.cart;

import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Cart;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.CartItemRepository;
import com.project.skin_me.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService implements ICartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Override
    public Cart getCart(Long id) {
        return cartRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
    }

    @Override
    public Optional<Cart> getUserActiveCart(User user) {
        return cartRepository.findByUserIdAndActiveWithRelations(user.getId(), true);
    }

    @Override
    @Transactional
    public void removeCart(Long id) {
        Cart cart = getCart(id);
        cartItemRepository.deleteByCartId(id);
        cart.getItems().clear();
        cartRepository.deleteById(id);
    }

    @Override
    public BigDecimal getTotalPrice(Long id) {
        Cart cart = getCart(id);
        return cart.getTotalAmount();
    }

    @Override
    public Cart initializeNewCart(User user) {
        Cart newCart = new Cart();
        newCart.setUser(user);
        newCart.setActive(true);
        newCart.setTotalAmount(BigDecimal.ZERO);
        return cartRepository.save(newCart);
    }

    @Override
    public Cart getCartByUserId(Long userId) {
        Cart cart = cartRepository.findByUserIdWithRelations(userId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart not found for user with ID: " + userId);
        }
        return cart;
    }

    @Override
    public List<Cart> getAllCarts() {
        return cartRepository.findAll();
    }

}
