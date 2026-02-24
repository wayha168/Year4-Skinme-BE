package com.project.skin_me.service.product;

import com.project.skin_me.enums.ProductStatus;
import com.project.skin_me.event.ProductAddedEvent;
import com.project.skin_me.event.ProductDeletedEvent;
import com.project.skin_me.event.ProductUpdatedEvent;
import com.project.skin_me.exception.AlreadyExistsException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.project.skin_me.dto.ImageDto;
import com.project.skin_me.dto.ProductDto;
import com.project.skin_me.exception.ProductNotFoundException;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Brand;
import com.project.skin_me.model.Image;
import com.project.skin_me.model.Product;
import com.project.skin_me.repository.BrandRepository;
import com.project.skin_me.repository.ImageRepository;
import com.project.skin_me.repository.ProductRepository;
import com.project.skin_me.request.AddProductRequest;
import com.project.skin_me.request.ProductUpdateRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService implements IProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ImageRepository imageRepository;
    private final ModelMapper modelMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Product addProduct(AddProductRequest request) {
        if (request.getBrandId() == null) {
            throw new ResourceNotFoundException("Brand is required!");
        }
        Brand brand = brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with ID: " + request.getBrandId()));
        if (productExists(brand.getId(), request.getName())) {
            throw new AlreadyExistsException(brand.getName() + " " + request.getName() + " already exists, you might need to update");
        }

        Product product = createProduct(request, brand);
        product.setCategory(brand.getCategory());
        product = productRepository.save(product);
        eventPublisher.publishEvent(new ProductAddedEvent(this));

        return product;
    }

    @Override
    public void deleteProductById(Long id) {
        productRepository.findById(id)
                .ifPresentOrElse(product -> {
                    product.setStatus(ProductStatus.INACTIVE);
                    productRepository.save(product);
                    eventPublisher.publishEvent(new ProductDeletedEvent(this));
                }, () -> {
                    throw new ProductNotFoundException("Product not found!");
                });
    }

    @Override
    public List<Product> getActiveProducts() {
        return productRepository.findByStatus(ProductStatus.ACTIVE);
    }

    @Override
    public Product updateProduct(ProductUpdateRequest product, Long productId) {
        return productRepository.findById(productId)
                .map(existingProduct -> {
                    Product updatedProduct = updateExistingProduct(existingProduct, product);
                    Product savedProduct = productRepository.save(updatedProduct);
                    // 🔥 PUBLISH EVENT AFTER SUCCESSFUL UPDATE
                    eventPublisher.publishEvent(new ProductUpdatedEvent(this));
                    return savedProduct;
                })
                .orElseThrow(() -> new ProductNotFoundException("Product not found!!"));
    }

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findAllWithCategory(); // Optimized: fetch category to avoid N+1
    }

    public List<Product> getAllProductsWithoutImages() {
        return productRepository.findAll();
    }

    @Override
    public Product getProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found!"));
    }

    private boolean productExists(Long brandId, String name) {
        return productRepository.existsByNameAndBrand_Id(name, brandId);
    }

    private Product createProduct(AddProductRequest request, Brand brand) {
        return new Product(
                request.getName(),
                request.getPrice(),
                request.getProductType(),
                request.getInventory(),
                request.getDescription(),
                request.getHowToUse(),
                brand);
    }

    private Product updateExistingProduct(Product existingProduct, ProductUpdateRequest request) {
        existingProduct.setName(request.getName());
        existingProduct.setPrice(request.getPrice());
        existingProduct.setProductType(request.getProductType());
        existingProduct.setInventory(request.getInventory());
        existingProduct.setDescription(request.getDescription());
        existingProduct.setHowToUse(request.getHowToUse());

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with ID: " + request.getBrandId()));
            existingProduct.setBrand(brand);
            existingProduct.setCategory(brand.getCategory());
        }

        if (request.getStatus() != null) {
            existingProduct.setStatus(request.getStatus());
        }

        return existingProduct;
    }

    @Override
    public List<Product> getAllProductsByCategory(String category) {
        // Use direct category relationship (previous relationship)
        return productRepository.findByCategoryNameWithCategory(category);
    }

    @Override
    public List<Product> getProductsByBrand(String brand) {
        return productRepository.findByBrand_Name(brand);
    }

    @Override
    public List<Product> getProductsByName(String name) {
        return productRepository.findByName(name);
    }

    @Override
    public List<Product> getProductsByProductType(String productType) {
        return productRepository.findByProductType(productType);
    }

    @Override
    public List<Product> getProductsByCategoryAndBrand(String category, String brand) {
        return productRepository.findByCategory_NameAndBrand(category, brand);
    }

    @Override
    public List<Product> getProductsByBrandAndName(String brand, String name) {
        return productRepository.findByBrandAndName(brand, name);
    }

    @Override
    public List<Product> getProductsByProductTypeAndName(String productType, String name) {
        return productRepository.findByProductTypeAndName(productType, name);
    }

    @Override
    public Long countProductsByBrandAndName(String brand, String name) {
        return productRepository.countByBrand_NameAndName(brand, name);
    }

    @Override
    public List<ProductDto> getConvertedProducts(List<Product> products) {
        return products.stream().map(this::convertToDto).toList();
    }

    @Override
    public ProductDto convertToDto(Product product) {
        ProductDto productDto = modelMapper.map(product, ProductDto.class);

        if (product.getBrand() != null) {
            productDto.setBrand(product.getBrand());
        }
        // Use direct category relationship (previous relationship)
        productDto.setCategory(product.getCategory());

        List<Image> images = imageRepository.findByProductId(product.getId());
        List<ImageDto> imageDtos = images.stream().map(image -> {
            ImageDto dto = new ImageDto();
            dto.setImageId(image.getId());
            dto.setFileName(image.getFileName());
            dto.setDownloadUrl(image.getDownloadUrl());
            return dto;
        }).toList();

        productDto.setImages(imageDtos);
        return productDto;
    }

    @Override
    public List<Product> getPopularProducts() {
        return productRepository.findAll()
                .stream()
                .filter(p -> p.getPopularProduct() != null)
                .toList();
    }

    private String escapeMarkdown(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }

    @Override
    public String toMarkdownTable(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return "_No products available._\n";
        }

        StringBuilder md = new StringBuilder();
        md.append("| ID | Name | Brand | Price | Type | Inventory | Category | Images |\n");
        md.append("|----|------|-------|-------|------|-----------|----------|--------|\n");

        for (Product p : products) {
            String images = (p.getImages() != null && !p.getImages().isEmpty())
                    ? p.getImages().stream()
                    .map(img -> String.format("[%s](%s)", escapeMarkdown(img.getFileName()), img.getDownloadUrl()))
                    .collect(Collectors.joining(", "))
                    : "_none_";

            String brandName = p.getBrand() != null ? escapeMarkdown(p.getBrand().getName()) : "_none_";
            String category = (p.getBrand() != null && p.getBrand().getCategory() != null)
                    ? escapeMarkdown(p.getBrand().getCategory().getName()) : "_none_";

            md.append(String.format("| %d | %s | %s | $%s | %s | %d | %s | %s |\n",
                    p.getId(),
                    escapeMarkdown(p.getName()),
                    brandName,
                    p.getPrice(),
                    escapeMarkdown(p.getProductType()),
                    p.getInventory(),
                    category,
                    images));
        }
        return md.toString();
    }
}