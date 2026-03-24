package com.project.skin_me.service.promotion;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.project.skin_me.dto.PromotionCheckoutSummaryDto;
import com.project.skin_me.dto.PromotionDto;
import com.project.skin_me.enums.PromotionType;
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
        validateCreateRequest(request);

        Promotion promotion = new Promotion();
        promotion.setPromotionType(request.getPromotionType());
        promotion.setTitle(request.getTitle());
        promotion.setDescription(request.getDescription());
        promotion.setLink(request.getLink());
        promotion.setDeadline(request.getDeadline());
        promotion.setStartDate(request.getStartDate());
        promotion.setActive(request.getActive() != null ? request.getActive() : true);

        applyTypeFieldsOnCreate(promotion, request);

        validatePromotionState(promotion);

        Promotion savedPromotion = promotionRepository.save(promotion);
        logger.info("Promotion created successfully: {}", savedPromotion.getId());

        return convertToDto(savedPromotion);
    }

    private void applyTypeFieldsOnCreate(Promotion promotion, CreatePromotionRequest request) {
        switch (request.getPromotionType()) {
            case PRODUCT_DISCOUNT -> {
                Product product = productRepository.findById(request.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));
                promotion.setProduct(product);
                promotion.setDiscountPercentage(request.getDiscountPercentage());
                promotion.setFreeDelivery(false);
                promotion.setMinimumOrderAmount(null);
            }
            case FREE_DELIVERY -> {
                promotion.setProduct(null);
                promotion.setDiscountPercentage(BigDecimal.ZERO);
                promotion.setFreeDelivery(true);
                promotion.setMinimumOrderAmount(normalizeMinAmount(request.getMinimumOrderAmount()));
            }
            case MIN_ORDER_SPEND -> {
                promotion.setProduct(null);
                promotion.setDiscountPercentage(request.getDiscountPercentage());
                promotion.setFreeDelivery(false);
                promotion.setMinimumOrderAmount(request.getMinimumOrderAmount().setScale(2, RoundingMode.HALF_UP));
            }
        }
    }

    private void validateCreateRequest(CreatePromotionRequest request) {
        if (request.getStartDate().isAfter(request.getDeadline())) {
            throw new IllegalArgumentException("Start date must be before deadline");
        }
        if (request.getStartDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }
        PromotionType type = request.getPromotionType();
        if (type == null) {
            throw new IllegalArgumentException("Promotion type is required");
        }
        switch (type) {
            case PRODUCT_DISCOUNT -> {
                if (request.getProductId() == null) {
                    throw new IllegalArgumentException("Product is required for a product discount");
                }
                if (request.getDiscountPercentage() == null
                        || request.getDiscountPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Discount percentage must be greater than 0 for product discount");
                }
            }
            case FREE_DELIVERY -> {
                if (request.getProductId() != null) {
                    throw new IllegalArgumentException("Free delivery promotions must not target a single product");
                }
                if (request.getDiscountPercentage() != null && request.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
                    throw new IllegalArgumentException("Use 0% discount for free delivery promotions");
                }
            }
            case MIN_ORDER_SPEND -> {
                if (request.getMinimumOrderAmount() == null
                        || request.getMinimumOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Minimum order amount must be greater than 0");
                }
                if (request.getDiscountPercentage() == null
                        || request.getDiscountPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Order discount percentage must be greater than 0");
                }
            }
        }
    }

    private BigDecimal normalizeMinAmount(BigDecimal min) {
        if (min == null || min.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return min.setScale(2, RoundingMode.HALF_UP);
    }

    private void validatePromotionState(Promotion p) {
        if (p.getStartDate().isAfter(p.getDeadline())) {
            throw new IllegalArgumentException("Start date must be before deadline");
        }
        PromotionType type = p.getPromotionType() != null ? p.getPromotionType() : PromotionType.PRODUCT_DISCOUNT;
        switch (type) {
            case PRODUCT_DISCOUNT -> {
                if (p.getProduct() == null) {
                    throw new IllegalArgumentException("Product is required for a product discount");
                }
                if (p.getDiscountPercentage() == null || p.getDiscountPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Discount percentage must be greater than 0 for product discount");
                }
            }
            case FREE_DELIVERY -> {
                if (p.getProduct() != null) {
                    throw new IllegalArgumentException("Free delivery promotions must not be linked to a product");
                }
            }
            case MIN_ORDER_SPEND -> {
                if (p.getMinimumOrderAmount() == null || p.getMinimumOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Minimum order amount must be greater than 0");
                }
                if (p.getDiscountPercentage() == null || p.getDiscountPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Order discount percentage must be greater than 0");
                }
                if (p.getProduct() != null) {
                    throw new IllegalArgumentException("Minimum order promotions apply to the whole cart, not one product");
                }
            }
        }
    }

    @Override
    @Transactional
    public PromotionDto updatePromotion(Long id, UpdatePromotionRequest request) {
        logger.debug("Updating promotion: {}", id);

        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with ID: " + id));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            promotion.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            promotion.setDescription(request.getDescription());
        }
        if (request.getLink() != null) {
            promotion.setLink(request.getLink());
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

        PromotionType newType = request.getPromotionType() != null ? request.getPromotionType() : promotion.getPromotionType();
        if (request.getPromotionType() != null) {
            promotion.setPromotionType(request.getPromotionType());
        }

        if (newType == PromotionType.PRODUCT_DISCOUNT) {
            if (request.getProductId() != null) {
                Product product = productRepository.findById(request.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));
                promotion.setProduct(product);
            }
            if (request.getDiscountPercentage() != null) {
                promotion.setDiscountPercentage(request.getDiscountPercentage());
            }
            promotion.setFreeDelivery(false);
            promotion.setMinimumOrderAmount(null);
        } else {
            promotion.setProduct(null);
            if (newType == PromotionType.FREE_DELIVERY) {
                promotion.setDiscountPercentage(BigDecimal.ZERO);
                promotion.setFreeDelivery(true);
                if (request.getMinimumOrderAmount() != null) {
                    promotion.setMinimumOrderAmount(normalizeMinAmount(request.getMinimumOrderAmount()));
                } else if (request.getPromotionType() == PromotionType.FREE_DELIVERY) {
                    promotion.setMinimumOrderAmount(null);
                }
            } else if (newType == PromotionType.MIN_ORDER_SPEND) {
                promotion.setFreeDelivery(false);
                if (request.getDiscountPercentage() != null) {
                    promotion.setDiscountPercentage(request.getDiscountPercentage());
                }
                if (request.getMinimumOrderAmount() != null) {
                    promotion.setMinimumOrderAmount(request.getMinimumOrderAmount().setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

        validatePromotionState(promotion);

        Promotion updatedPromotion = promotionRepository.save(promotion);
        logger.info("Promotion updated successfully: {}", id);

        return convertToDto(updatedPromotion);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionDto getPromotionById(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with ID: " + id));
        if (promotion.getImages() != null) {
            promotion.getImages().size();
        }
        return convertToDto(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDto> getAllPromotions() {
        List<Promotion> promotions = promotionRepository.findAllByOrderByCreatedAtDesc();
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
    public List<PromotionDto> getActivePromotionsByType(PromotionType type) {
        List<Promotion> promotions = promotionRepository.findActivePromotionsByType(type, LocalDateTime.now());
        return promotions.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionCheckoutSummaryDto getActiveCheckoutSummary() {
        List<PromotionDto> active = getActivePromotions();
        PromotionCheckoutSummaryDto summary = new PromotionCheckoutSummaryDto();
        for (PromotionDto p : active) {
            if (p.getPromotionType() == null) {
                continue;
            }
            switch (PromotionType.valueOf(p.getPromotionType())) {
                case PRODUCT_DISCOUNT -> summary.getProductDiscounts().add(p);
                case FREE_DELIVERY -> summary.getFreeDeliveryOffers().add(p);
                case MIN_ORDER_SPEND -> summary.getMinimumOrderDiscounts().add(p);
            }
        }
        return summary;
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
        PromotionType type = promotion.getPromotionType() != null ? promotion.getPromotionType() : PromotionType.PRODUCT_DISCOUNT;
        dto.setPromotionType(type.name());
        dto.setPromotionTypeLabel(promotionTypeLabel(type));
        dto.setSummaryLine(buildSummaryLine(promotion, type));
        dto.setTitle(promotion.getTitle());
        dto.setDescription(promotion.getDescription());
        dto.setLink(promotion.getLink());
        dto.setDiscountPercentage(promotion.getDiscountPercentage());
        dto.setMinimumOrderAmount(promotion.getMinimumOrderAmount());
        dto.setFreeDelivery(promotion.isFreeDelivery());
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

    private static String promotionTypeLabel(PromotionType type) {
        return switch (type) {
            case PRODUCT_DISCOUNT -> "Product discount";
            case FREE_DELIVERY -> "Free delivery";
            case MIN_ORDER_SPEND -> "Minimum order discount";
        };
    }

    private static String buildSummaryLine(Promotion p, PromotionType type) {
        return switch (type) {
            case PRODUCT_DISCOUNT -> {
                String name = p.getProduct() != null ? p.getProduct().getName() : "product";
                yield String.format("%s%% off %s", p.getDiscountPercentage().stripTrailingZeros().toPlainString(), name);
            }
            case FREE_DELIVERY -> {
                if (p.getMinimumOrderAmount() != null && p.getMinimumOrderAmount().compareTo(BigDecimal.ZERO) > 0) {
                    yield "Free delivery on orders from $" + p.getMinimumOrderAmount().stripTrailingZeros().toPlainString();
                }
                yield "Free delivery";
            }
            case MIN_ORDER_SPEND -> String.format(
                    "%s%% off order when spend is at least $%s",
                    p.getDiscountPercentage().stripTrailingZeros().toPlainString(),
                    p.getMinimumOrderAmount() != null ? p.getMinimumOrderAmount().stripTrailingZeros().toPlainString() : "—");
        };
    }

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
