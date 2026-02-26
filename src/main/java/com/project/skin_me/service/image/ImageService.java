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
                image.setImage(null);
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
            image.setImage(null);
            imageRepository.save(image);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

}
