package com.project.skin_me.service.cart;

import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Cart;
import com.project.skin_me.model.CartItem;
import com.project.skin_me.model.Product;
import com.project.skin_me.repository.CartRepository;
import com.project.skin_me.service.product.IProductService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartItemService implements ICartItemService {

    private final CartRepository cartRepository;
    private final IProductService productService;
    private final ICartService cartService;

    @Override
    @Transactional
    public void addItemToCart(Cart cart, Long productId, int quantity) {
        Product product = productService.getProductById(productId);

        Optional<CartItem> cartItemOpt = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();

        if (cartItemOpt.isPresent()) {
            CartItem existingItem = cartItemOpt.get();
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            existingItem.setUnitPrice(product.getPrice());
            existingItem.setTotalPrice();
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            newItem.setUnitPrice(product.getPrice());
            newItem.setTotalPrice();
            cart.addItem(newItem);
        }

        recalculateCartTotal(cart);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void removeItemFromCart(Long cartId, Long productId) {
        Cart cart = cartService.getCart(cartId);
        CartItem itemToRemove = getCartItem(cartId, productId);
        cart.removeItem(itemToRemove);
        recalculateCartTotal(cart);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void updateItemQuantity(Long cartId, Long productId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        Cart cart = cartService.getCart(cartId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found for product id: " + productId));
        item.setQuantity(quantity);
        item.setUnitPrice(item.getProduct().getPrice());
        item.setTotalPrice();
        recalculateCartTotal(cart);
        cartRepository.save(cart);
    }

    public CartItem getCartItem(Long cartId, Long productId) {
        Cart cart = cartService.getCart(cartId);
        return cart.getItems()
                .stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Item not found"));
    }

    private void recalculateCartTotal(Cart cart) {
        BigDecimal total = cart.getItems().stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalAmount(total);
    }
}
