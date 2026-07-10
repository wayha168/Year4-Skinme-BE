package com.project.skin_me.service.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

import com.project.skin_me.dto.ImageDto;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Category;
import com.project.skin_me.model.Image;
import com.project.skin_me.model.Product;
import com.project.skin_me.repository.ImageRepository;
import com.project.skin_me.service.product.IProductService;

@Service
@RequiredArgsConstructor
public class ImageService implements IImageService {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    private final ImageRepository imageRepository;
    private final IProductService productService;

    @Override
    public Image getImageById(Long id) {
        return imageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No image with this id" + id));
    }

    @Override
    public List<ImageDto> getImagesByProductId(Long productId) {
        List<Image> images = imageRepository.findByProductId(productId);
        List<ImageDto> dtos = new ArrayList<>();
        for (Image img : images) {
            ImageDto dto = new ImageDto();
            dto.setImageId(img.getId());
            dto.setFileName(img.getFileName());
            dto.setDownloadUrl(img.getDownloadUrl());
            dtos.add(dto);
        }
        return dtos;
    }

    @Override
    public void deleteImageById(Long id) {
        imageRepository.findById(id).ifPresentOrElse(imageRepository::delete, () -> {
            throw new ResourceNotFoundException("No image with this ID" + id);
        });
    }

    @Override
    public List<ImageDto> saveImages(Long productId, List<MultipartFile> files) {
        Product product = productService.getProductById(productId);
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + e.getMessage());
        }

        List<ImageDto> saveImageDto = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || originalFilename.isBlank()) {
                    originalFilename = "image_" + System.currentTimeMillis() + ".dat";
                }
                if (originalFilename.contains("..")) {
                    originalFilename = originalFilename.replace("..", "");
                }

                Path targetFile = basePath.resolve(originalFilename);
                Files.write(targetFile, file.getBytes());

                Image image = new Image();
                image.setFileName(originalFilename);
                image.setFileType(file.getContentType());
                image.setProduct(product);
                image.setDownloadUrl("/uploads/" + originalFilename);
                image.setImage(file.getBytes());
                Image saveImage = imageRepository.save(image);

                ImageDto imageDto = new ImageDto();
                imageDto.setImageId(saveImage.getId());
                imageDto.setFileName(saveImage.getFileName());
                imageDto.setDownloadUrl(saveImage.getDownloadUrl());
                saveImageDto.add(imageDto);

            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return saveImageDto;
    }

    @Override
    public String saveCategoryImage(MultipartFile file, Category category) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + e.getMessage());
        }
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = "image_" + System.currentTimeMillis() + ".dat";
            }
            if (originalFilename.contains("..")) {
                originalFilename = originalFilename.replace("..", "");
            }
            Path targetFile = basePath.resolve(originalFilename);
            Files.write(targetFile, file.getBytes());

            Image image = new Image();
            image.setFileName(originalFilename);
            image.setFileType(file.getContentType());
            image.setCategory(category);
            image.setDownloadUrl("/uploads/" + originalFilename);
            image.setImage(file.getBytes());
            imageRepository.save(image);
            return "/uploads/" + originalFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save category image: " + e.getMessage());
        }
    }

    @Override
    public String saveBrandImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + e.getMessage());
        }
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = "brand_" + System.currentTimeMillis() + ".dat";
            }
            if (originalFilename.contains("..")) {
                originalFilename = originalFilename.replace("..", "");
            }
            String ext = "";
            int dot = originalFilename.lastIndexOf('.');
            if (dot > 0 && dot < originalFilename.length() - 1) {
                ext = originalFilename.substring(dot);
            } else {
                ext = ".png";
            }
            String filename = "brand_" + System.currentTimeMillis() + ext;
            Path targetFile = basePath.resolve(filename);
            Files.write(targetFile, file.getBytes());

            Image image = new Image();
            image.setFileName(filename);
            image.setFileType(file.getContentType());
            image.setDownloadUrl("/uploads/" + filename);
            image.setImage(file.getBytes());
            imageRepository.save(image);
            return "/uploads/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save brand image: " + e.getMessage());
        }
    }

    @Override
    public String saveFeedbackImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String ct = file.getContentType();
        if (ct != null && !ct.isBlank() && !ct.startsWith("image/")) {
            throw new IllegalArgumentException("Feedback image must be an image file.");
        }
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + e.getMessage());
        }
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = "feedback_" + System.currentTimeMillis() + ".dat";
            }
            if (originalFilename.contains("..")) {
                originalFilename = originalFilename.replace("..", "");
            }
            String ext = "";
            int dot = originalFilename.lastIndexOf('.');
            if (dot > 0 && dot < originalFilename.length() - 1) {
                ext = originalFilename.substring(dot);
            } else {
                ext = ".png";
            }
            String filename = "feedback_" + System.currentTimeMillis() + ext;
            Path targetFile = basePath.resolve(filename);
            Files.write(targetFile, file.getBytes());

            Image image = new Image();
            image.setFileName(filename);
            image.setFileType(file.getContentType());
            image.setDownloadUrl("/uploads/" + filename);
            image.setImage(file.getBytes());
            imageRepository.save(image);
            return "/uploads/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save feedback image: " + e.getMessage());
        }
    }

    @Override
    public void updateImage(MultipartFile file, Long imageId) {
        Image image = getImageById(imageId);
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = "image_" + System.currentTimeMillis() + ".dat";
            }
            if (originalFilename.contains("..")) {
                originalFilename = originalFilename.replace("..", "");
            }
            Path targetFile = basePath.resolve(originalFilename);
            Files.write(targetFile, file.getBytes());
            image.setFileName(originalFilename);
            image.setFileType(file.getContentType());
            image.setDownloadUrl("/uploads/" + originalFilename);
            image.setImage(file.getBytes());
            imageRepository.save(image);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

}
