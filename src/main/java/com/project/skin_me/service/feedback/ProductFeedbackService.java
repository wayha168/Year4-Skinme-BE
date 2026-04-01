package com.project.skin_me.service.feedback;

import com.project.skin_me.dto.ProductFeedbackDto;
import com.project.skin_me.dto.ProductFeedbackEventDto;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Product;
import com.project.skin_me.model.ProductFeedback;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.ProductFeedbackRepository;
import com.project.skin_me.repository.ProductRepository;
import com.project.skin_me.request.ProductFeedbackRequest;
import com.project.skin_me.service.image.IImageService;
import com.project.skin_me.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductFeedbackService implements IProductFeedbackService {

    private final ProductFeedbackRepository productFeedbackRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final IImageService imageService;

    @Override
    @Transactional
    public ProductFeedbackDto submit(User user, ProductFeedbackRequest request, MultipartFile image) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (productFeedbackRepository.findByUser_IdAndProduct_Id(user.getId(), product.getId()).isPresent()) {
            throw new IllegalStateException("You have already submitted feedback for this product.");
        }

        BigDecimal rating = request.getRating().setScale(2, RoundingMode.HALF_UP);
        if (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(new BigDecimal("5.00")) > 0) {
            throw new IllegalArgumentException("Rating must be between 0 and 5.");
        }

        ProductFeedback fb = new ProductFeedback();
        fb.setUser(user);
        fb.setProduct(product);
        fb.setRating(rating);
        fb.setComment(request.getComment() != null ? request.getComment().trim() : null);
        String imageUrl = imageService.saveFeedbackImage(image);
        fb.setImageUrl(imageUrl);
        fb.setVisibleOnFrontend(false);

        ProductFeedback saved = productFeedbackRepository.save(fb);

        String preview = saved.getComment();
        if (preview != null && preview.length() > 120) {
            preview = preview.substring(0, 117) + "...";
        }
        ProductFeedbackEventDto event = ProductFeedbackEventDto.builder()
                .feedbackId(saved.getId())
                .productId(product.getId())
                .productName(product.getName())
                .rating(saved.getRating())
                .userEmail(user.getEmail())
                .commentPreview(preview)
                .createdAtEpochMs(saved.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
        messagingTemplate.convertAndSend("/topic/feedback", event);

        try {
            notificationService.notifyAdminsNewProductFeedback(
                    product.getName(), saved.getRating(), saved.getComment(), user.getEmail());
        } catch (Exception ignored) {
            // feedback already saved; notification is best-effort
        }

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductFeedbackDto> listVisibleForProduct(Long productId, Pageable pageable) {
        return productFeedbackRepository
                .findByProduct_IdAndVisibleOnFrontendTrueOrderByCreatedAtDesc(productId, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductFeedbackDto> listAllForAdmin(Pageable pageable) {
        return productFeedbackRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Override
    @Transactional
    public ProductFeedbackDto setVisible(Long feedbackId, boolean visible) {
        ProductFeedback fb = productFeedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));
        fb.setVisibleOnFrontend(visible);
        return toDto(productFeedbackRepository.save(fb));
    }

    private ProductFeedbackDto toDto(ProductFeedback fb) {
        User u = fb.getUser();
        Product p = fb.getProduct();
        String display = "";
        if (u != null) {
            String fn = Optional.ofNullable(u.getFirstName()).orElse("");
            String ln = Optional.ofNullable(u.getLastName()).orElse("");
            display = (fn + " " + ln).trim();
            if (display.isEmpty()) {
                display = u.getEmail() != null ? u.getEmail() : ("User #" + u.getId());
            }
        }
        return ProductFeedbackDto.builder()
                .id(fb.getId())
                .productId(p != null ? p.getId() : null)
                .productName(p != null ? p.getName() : null)
                .userId(u != null ? u.getId() : null)
                .userDisplayName(display)
                .rating(fb.getRating())
                .comment(fb.getComment())
                .imageUrl(fb.getImageUrl())
                .visibleOnFrontend(fb.isVisibleOnFrontend())
                .createdAt(fb.getCreatedAt())
                .build();
    }
}
