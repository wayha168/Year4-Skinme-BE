package com.project.skin_me.repository;

import com.project.skin_me.model.FavoriteItem;
import com.project.skin_me.model.Product;
import com.project.skin_me.model.FavoriteList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FavoriteItemRepository extends JpaRepository<FavoriteItem, Long> {
    Optional<FavoriteItem> findByFavoriteListAndProduct(FavoriteList favoriteList, Product product);

    Optional<FavoriteItem> findByFavoriteList_IdAndProductId(Long favoriteListId, Long productId);
    
    @Query("SELECT fi FROM FavoriteItem fi JOIN FETCH fi.favoriteList fl JOIN FETCH fl.user JOIN FETCH fi.product p LEFT JOIN FETCH p.images ORDER BY fi.id DESC")
    List<FavoriteItem> findRecentFavoritesWithRelations();
}
