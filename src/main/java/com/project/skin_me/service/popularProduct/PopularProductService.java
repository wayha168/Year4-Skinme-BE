package com.project.skin_me.service.popularProduct;

import com.project.skin_me.dto.PopularProductDto;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.OrderItem;
import com.project.skin_me.model.PopularProduct;
import com.project.skin_me.model.Product;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.PopularProductRepository;
import com.project.skin_me.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class PopularProductService implements IPopularProductService {

    private final PopularProductRepository popularProductRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ModelMapper modelMapper;
    private static final int POPULARITY_THRESHOLD = 10;

    @Override
    public void saveFromOrder(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            int added = item.getQuantity();
            product.setTotalOrders(product.getTotalOrders() + added);
            productRepository.save(product);

            PopularProduct popular = popularProductRepository.findByProductId(product.getId()).orElse(null);
            if (popular == null) {
                if (product.getTotalOrders() >= POPULARITY_THRESHOLD) {
                    popular = new PopularProduct();
                    popular.setProduct(product);
                    popular.setQuantitySold(product.getTotalOrders());
                    popular.setLastPurchasedDate(LocalDateTime.now());
                    popularProductRepository.save(popular);
                    product.setPopularProduct(popular);
                    productRepository.save(product);
                }
            } else {
                popular.setQuantitySold(product.getTotalOrders());
                popular.setLastPurchasedDate(LocalDateTime.now());
                popularProductRepository.save(popular);
            }
        }
    }

    @Override
    public Optional<PopularProduct> findByProductId(Long productId) {
        return popularProductRepository.findByProductId(productId);
    }

    public List<PopularProductDto> getPopularProducts() {
        return popularProductRepository.findAll()
                .stream()
                .filter(pop -> pop.getQuantitySold() >= POPULARITY_THRESHOLD)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    public List<PopularProductDto> getAllProductsWithSales() {
        return productRepository.findAll()
                .stream()
                .map(product -> {
                    PopularProduct popular = popularProductRepository.findByProductId(product.getId()).orElse(null);
                    PopularProductDto dto = new PopularProductDto();
                    dto.setProductId(product.getId());
                    dto.setName(product.getName());
                    dto.setPrice(product.getPrice());
                    dto.setBrand(product.getBrand() != null ? product.getBrand().getName() : null);
                    dto.setQuantitySold(popular != null ? popular.getQuantitySold() : product.getTotalOrders());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<PopularProductDto> getUserSales(Long userId) {
        // Fetch orders for the user
        List<Order> orders = orderRepository.findByUserId(userId);
        // Aggregate quantitySold per product from order items
        Map<Long, Integer> productQuantities = orders.stream()
                .flatMap(order -> order.getOrderItems().stream())
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        Collectors.summingInt(OrderItem::getQuantity)
                ));

        // Convert to PopularProductDto
        return productQuantities.entrySet().stream()
                .map(entry -> {
                    Long productId = entry.getKey();
                    Integer quantitySold = entry.getValue();
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
                    PopularProductDto dto = new PopularProductDto();
                    dto.setProductId(productId);
                    dto.setName(product.getName());
                    dto.setPrice(product.getPrice());
                    dto.setBrand(product.getBrand() != null ? product.getBrand().getName() : null);
                    dto.setQuantitySold(quantitySold);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public PopularProductDto convertToDto(PopularProduct popularProduct) {
        PopularProductDto dto = modelMapper.map(popularProduct, PopularProductDto.class);
        Product product = popularProduct.getProduct();
        dto.setName(product.getName());
        dto.setPrice(product.getPrice());
        dto.setBrand(product.getBrand() != null ? product.getBrand().getName() : null);
        return dto;
    }


}