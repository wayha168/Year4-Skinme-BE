package com.project.skin_me.controller.api;

import com.project.skin_me.dto.ProductFeedbackDto;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.feedback.IProductFeedbackService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${api.prefix}/admin/feedback")
@RequiredArgsConstructor
public class AdminProductFeedbackController {

    private final IProductFeedbackService productFeedbackService;

    /** Paginated list (max 100 per page). */
    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductFeedbackDto> result = productFeedbackService.listAllForAdmin(pageable);
        return ResponseEntity.ok(new ApiResponse("OK", result));
    }

    /** All feedback rows in one response (capped for safety). Default max 1000. */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> listAll(
            @RequestParam(name = "max", defaultValue = "1000") int max) {
        int capped = Math.min(Math.max(max, 1), 2000);
        Pageable pageable = PageRequest.of(0, capped, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ProductFeedbackDto> list = productFeedbackService.listAllForAdmin(pageable).getContent();
        return ResponseEntity.ok(new ApiResponse("OK", list));
    }

    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> setVisibility(@PathVariable Long id,
            @RequestBody(required = false) VisibilityBody body) {
        boolean visible = body != null && body.isVisibleOnFrontend();
        ProductFeedbackDto dto = productFeedbackService.setVisible(id, visible);
        return ResponseEntity.ok(new ApiResponse("Updated", dto));
    }

    @Data
    public static class VisibilityBody {
        private boolean visibleOnFrontend;
    }
}
