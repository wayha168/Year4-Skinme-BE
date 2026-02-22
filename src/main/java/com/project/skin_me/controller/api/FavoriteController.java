package com.project.skin_me.controller.api;

import com.project.skin_me.dto.FavoriteProductDto;
import com.project.skin_me.exception.AlreadyExistsException;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.favorite.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("${api.prefix}/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/add")
    public ResponseEntity<ApiResponse> addFavorite(@RequestParam Long userId,
                                                   @RequestParam Long productId) {
        try {
            FavoriteProductDto dto = favoriteService.addFavorite(userId, productId);
            return ResponseEntity.ok(new ApiResponse("Product added to favorites", dto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), null));
        } catch (AlreadyExistsException e) {
            return ResponseEntity.status(CONFLICT)
                    .body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error adding favorite", e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse> getFavorites(@PathVariable Long userId) {
        try {
            List<FavoriteProductDto> list = favoriteService.getFavoritesByUser(userId);
            return ResponseEntity.ok(new ApiResponse("Favorites retrieved", list));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving favorites", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse> getAllFavorites() {
        try {
            List<FavoriteProductDto> list = favoriteService.getAllFavorites();
            return ResponseEntity.ok(new ApiResponse("All favorites retrieved", list));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error retrieving all favorites", e.getMessage()));
        }
    }

    @DeleteMapping("/remove")
    public ResponseEntity<ApiResponse> removeFavorite(@RequestParam Long userId,
                                                      @RequestParam Long productId) {
        try {
            favoriteService.removeFavorite(userId, productId);
            return ResponseEntity.ok(new ApiResponse("Favorite removed", null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error removing favorite", e.getMessage()));
        }
    }
    @DeleteMapping("/remove/by-product")
    public ResponseEntity<ApiResponse> removeFavoriteByProductId(@RequestParam Long userId,
                                                                 @RequestParam Long productId) {
        try {
            favoriteService.removeFavoriteById(userId, productId);
            return ResponseEntity.ok(new ApiResponse("Favorite product removed successfully", null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error removing favorite by product ID", e.getMessage()));
        }
    }
}
