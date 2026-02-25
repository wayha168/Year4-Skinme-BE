package com.project.skin_me.service.promotion;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.project.skin_me.dto.ImageDto;
import com.project.skin_me.dto.PromotionDto;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Image;
import com.project.skin_me.model.Product;
import com.project.skin_me.model.Promotion;
import com.project.skin_me.repository.ImageRepository;
import com.project.skin_me.repository.ProductRepository;
import com.project.skin_me.repository.PromotionRepository;
import com.project.skin_me.request.CreatePromotionRequest;
import com.project.skin_me.request.UpdatePromotionRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromotionService implements IPromotionService {
    
    private static final Logger logger = LoggerFactory.getLogger(PromotionService.class);
    private final PromotionRepository promotionRepository;
    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;

    @Override
    @Transactional
    public PromotionDto createPromotion(CreatePromotionRequest request) {
        logger.debug("Creating promotion: {}", request.getTitle());
        
        // Validate product exists
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));
        
        // Validate dates
        if (request.getStartDate().isAfter(request.getDeadline())) {
            throw new IllegalArgumentException("Start date must be before deadline");
        }
        
        if (request.getStartDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }
        
        // Create promotion
        Promotion promotion = new Promotion();
        promotion.setTitle(request.getTitle());
        promotion.setDescription(request.getDescription());
        promotion.setLink(request.getLink());
        promotion.setDiscountPercentage(request.getDiscountPercentage());
        promotion.setDeadline(request.getDeadline());
        promotion.setStartDate(request.getStartDate());
        promotion.setProduct(product);
        promotion.setActive(request.getActive() != null ? request.getActive() : true);
        
        Promotion savedPromotion = promotionRepository.save(promotion);
        logger.info("Promotion created successfully: {}", savedPromotion.getId());
        
        return convertToDto(savedPromotion);
    }

    @Override
    @Transactional
    public PromotionDto updatePromotion(Long id, UpdatePromotionRequest request) {
        logger.debug("Updating promotion: {}", id);
        
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with ID: " + id));
        
        // Update fields if provided
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            promotion.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            promotion.setDescription(request.getDescription());
        }
        if (request.getLink() != null) {
            promotion.setLink(request.getLink());
        }
        if (request.getDiscountPercentage() != null) {
            promotion.setDiscountPercentage(request.getDiscountPercentage());
        }
        if (request.getDeadline() != null) {
            promotion.setDeadline(request.getDeadline());
        }
        if (request.getStartDate() != null) {
            promotion.setStartDate(request.getStartDate());
        }
        if (request.getActive() != null) {
            promotion.setActive(request.getActive());
        }
        if (request.getProductId() != null) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));
            promotion.setProduct(product);
        }
        
        // Validate dates if updated
        if (promotion.getStartDate().isAfter(promotion.getDeadline())) {
            throw new IllegalArgumentException("Start date must be before deadline");
        }
        
        Promotion updatedPromotion = promotionRepository.save(promotion);
        logger.info("Promotion updated successfully: {}", id);
        
        return convertToDto(updatedPromotion);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionDto getPromotionById(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with ID: " + id));
        // Force load images
        if (promotion.getImages() != null) {
            promotion.getImages().size();
        }
        return convertToDto(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDto> getAllPromotions() {
        List<Promotion> promotions = promotionRepository.findAllByOrderByCreatedAtDesc();
        // Force load images for all promotions
        promotions.forEach(p -> {
            if (p.getImages() != null) {
                p.getImages().size();
            }
        });
        return promotions.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PromotionDto> getAllPromotions(Pageable pageable) {
        Page<Promotion> page = promotionRepository.findAllByOrderByCreatedAtDesc(pageable);
        List<PromotionDto> dtos = page.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDto> getActivePromotions() {
        List<Promotion> promotions = promotionRepository.findActivePromotions(LocalDateTime.now());
        return promotions.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionDto getActivePromotionByProductId(Long productId) {
        Promotion promotion = promotionRepository.findActivePromotionByProductId(productId, LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("No active promotion found for product ID: " + productId));
        return convertToDto(promotion);
    }

    @Override
    @Transactional
    public void deletePromotion(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with ID: " + id));
        promotionRepository.delete(promotion);
        logger.info("Promotion deleted successfully: {}", id);
    }

    @Override
    public PromotionDto convertToDto(Promotion promotion) {
        PromotionDto dto = new PromotionDto();
        dto.setId(promotion.getId());
        dto.setTitle(promotion.getTitle());
        dto.setDescription(promotion.getDescription());
        dto.setLink(promotion.getLink());
        dto.setDiscountPercentage(promotion.getDiscountPercentage());
        dto.setDeadline(promotion.getDeadline());
        dto.setStartDate(promotion.getStartDate());
        dto.setActive(promotion.isActive());
        dto.setCreatedAt(promotion.getCreatedAt());
        dto.setUpdatedAt(promotion.getUpdatedAt());
        dto.setCurrentlyActive(promotion.isCurrentlyActive());
        dto.setExpired(promotion.getDeadline() != null && promotion.getDeadline().isBefore(LocalDateTime.now()));

        if (promotion.getProduct() != null) {
            dto.setProductId(promotion.getProduct().getId());
            dto.setProductName(promotion.getProduct().getName());
            dto.setOriginalPrice(promotion.getProduct().getPrice());
            dto.setDiscountedPrice(promotion.calculateDiscountedPrice());
        }
        
        // Convert images to DTOs
        if (promotion.getImages() != null && !promotion.getImages().isEmpty()) {
            List<ImageDto> imageDtos = promotion.getImages().stream()
                    .map(image -> {
                        ImageDto imageDto = new ImageDto();
                        imageDto.setImageId(image.getId());
                        imageDto.setFileName(image.getFileName());
                        String url = (image.getFileName() != null && !image.getFileName().isBlank())
                                ? "/uploads/" + image.getFileName()
                                : (image.getDownloadUrl() != null ? image.getDownloadUrl() : "");
                        imageDto.setDownloadUrl(url);
                        return imageDto;
                    })
                    .collect(Collectors.toList());
            dto.setImages(imageDtos);
        }
        
        return dto;
    }

    /**
     * Save images for a promotion
     */
    @Transactional
    public List<ImageDto> savePromotionImages(Long promotionId, List<MultipartFile> files) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with ID: " + promotionId));

        List<ImageDto> savedImageDtos = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                Image image = new Image();
                image.setFileName(file.getOriginalFilename());
                image.setFileType(file.getContentType());
                image.setImage(file.getBytes());
                image.setPromotion(promotion);

                Image savedImage = imageRepository.save(image);

                String fileName = savedImage.getFileName();
                String downloadUrl = (fileName != null && !fileName.isBlank()) ? "/uploads/" + fileName : "";
                savedImage.setDownloadUrl(downloadUrl);
                imageRepository.save(savedImage);

                ImageDto imageDto = new ImageDto();
                imageDto.setImageId(savedImage.getId());
                imageDto.setFileName(savedImage.getFileName());
                imageDto.setDownloadUrl(downloadUrl);
                savedImageDtos.add(imageDto);

            } catch (IOException e) {
                logger.error("Error saving image for promotion ID: {}", promotionId, e);
                throw new RuntimeException("Failed to save image: " + e.getMessage());
            }
        }
        return savedImageDtos;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateDiscountedPrice(Long productId) {
        try {
            Promotion promotion = promotionRepository.findActivePromotionByProductId(productId, LocalDateTime.now())
                    .orElse(null);
            
            if (promotion != null && promotion.isCurrentlyActive()) {
                return promotion.calculateDiscountedPrice();
            }
            
            // Return original price if no active promotion
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
            return product.getPrice();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error calculating discounted price for product ID: {}", productId, e);
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
            return product.getPrice();
        }
    }
}
