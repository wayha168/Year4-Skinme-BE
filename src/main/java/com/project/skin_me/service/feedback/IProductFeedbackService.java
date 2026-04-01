package com.project.skin_me.service.feedback;

import com.project.skin_me.dto.ProductFeedbackDto;
import com.project.skin_me.model.User;
import com.project.skin_me.request.ProductFeedbackRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface IProductFeedbackService {

    ProductFeedbackDto submit(User user, ProductFeedbackRequest request, MultipartFile image);

    Page<ProductFeedbackDto> listVisibleForProduct(Long productId, Pageable pageable);

    Page<ProductFeedbackDto> listAllForAdmin(Pageable pageable);

    ProductFeedbackDto setVisible(Long feedbackId, boolean visible);
}
