package com.project.skin_me.service.image;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.project.skin_me.dto.ImageDto;
import com.project.skin_me.model.Category;
import com.project.skin_me.model.Image;

public interface IImageService {
    Image getImageById(Long id);

    void deleteImageById(Long id);

    List<ImageDto> saveImages(Long productId, List<MultipartFile> files);

    /**
     * Save a single image for a category (like product images). Returns the download URL to store in category.image.
     */
    String saveCategoryImage(MultipartFile file, Category category);

    void updateImage(MultipartFile file, Long imageId);
}
