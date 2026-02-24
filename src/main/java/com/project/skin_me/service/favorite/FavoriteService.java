package com.project.skin_me.service.favorite;

import com.project.skin_me.dto.FavoriteProductDto;
import com.project.skin_me.exception.AlreadyExistsException;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.*;
import com.project.skin_me.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteService implements IFavoriteService {

    private final FavoriteListRepository favoriteListRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public FavoriteProductDto addFavorite(Long userId, Long productId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        FavoriteList favoriteList = favoriteListRepository.findByUser(user)
                .orElseGet(() -> {
                    FavoriteList newList = new FavoriteList();
                    newList.setUser(user);
                    return favoriteListRepository.save(newList);
                });

        favoriteItemRepository.findByFavoriteListAndProduct(favoriteList, product)
                .ifPresent(f -> { throw new AlreadyExistsException("Product already in favorites"); });

        FavoriteItem item = FavoriteItem.builder()
                .favoriteList(favoriteList)
                .product(product)
                .build();

        favoriteList.addItem(item);
        favoriteListRepository.save(favoriteList);

        return convertToDto(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteProductDto> getFavoritesByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        FavoriteList favoriteList = favoriteListRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Favorites not found for user"));

        return favoriteList.getItems().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeFavorite(Long userId, Long productId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        FavoriteList favoriteList = favoriteListRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Favorites not found for user"));

        FavoriteItem item = favoriteItemRepository.findByFavoriteListAndProduct(favoriteList, product)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite item not found"));

        favoriteList.removeItem(item);
        favoriteListRepository.save(favoriteList);
    }

    @Override
    @Transactional
    public void removeFavoriteById(Long userId, Long productId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        FavoriteList favoriteList = favoriteListRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Favorites not found for user"));

        FavoriteItem item = favoriteItemRepository
                .findByFavoriteList_IdAndProductId(favoriteList.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite item not found for product ID: " + productId));

        favoriteList.removeItem(item);
        favoriteItemRepository.delete(item);
    }

    @Override
    public FavoriteProductDto convertToDto(Object obj) {
        FavoriteItem item = (FavoriteItem) obj;
        Product product = item.getProduct();
        String thumbnailUrl = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().getFirst().getDownloadUrl() : null;

        return FavoriteProductDto.builder()
                .id(item.getId())
                .userId(item.getFavoriteList().getUser().getId())
                .productId(product.getId())
                .product(product)
                .productName(product.getName())
                .productBrand(product.getBrand() != null ? product.getBrand().getName() : null)
                .productThumbnailUrl(thumbnailUrl)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteProductDto> getAllFavorites() {
        List<FavoriteItem> items = favoriteItemRepository.findAll();
        if (items.isEmpty()) {
            throw new ResourceNotFoundException("No favorites found in the system");
        }
        return items.stream().map(this::convertToDto).collect(Collectors.toList());
    }
}
