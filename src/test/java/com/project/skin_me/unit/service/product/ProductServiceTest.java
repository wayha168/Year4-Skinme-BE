package com.project.skin_me.unit.service.product;

import com.project.skin_me.event.ProductAddedEvent;
import com.project.skin_me.exception.AlreadyExistsException;
import com.project.skin_me.exception.ProductNotFoundException;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Brand;
import com.project.skin_me.model.Category;
import com.project.skin_me.model.Product;
import com.project.skin_me.repository.BrandRepository;
import com.project.skin_me.repository.FavoriteItemRepository;
import com.project.skin_me.repository.ImageRepository;
import com.project.skin_me.repository.ProductRepository;
import com.project.skin_me.request.AddProductRequest;
import com.project.skin_me.service.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private FavoriteItemRepository favoriteItemRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final ModelMapper modelMapper = new ModelMapper();

    private ProductService productService;

    private Brand brand;
    private AddProductRequest request;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepository,
                brandRepository,
                imageRepository,
                favoriteItemRepository,
                modelMapper);
        ReflectionTestUtils.setField(productService, "eventPublisher", eventPublisher);

        Category category = new Category();
        category.setId(1L);
        category.setName("Skincare");

        brand = new Brand();
        brand.setId(10L);
        brand.setName("TestBrand");
        brand.setCategory(category);

        request = new AddProductRequest();
        request.setBrandId(10L);
        request.setName("Vitamin C");
        request.setPrice(new BigDecimal("29.99"));
        request.setProductType("Serum");
        request.setInventory(20);
        request.setDescription("Brightening");
        request.setHowToUse("AM");
        request.setSkinType("All");
        request.setBenefit("Glow");
    }

    @Test
    void getProductById_throwsWhenMissing() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProductById_returnsProduct() {
        Product p = new Product();
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThat(productService.getProductById(1L)).isSameAs(p);
    }

    @Test
    void addProduct_throwsWhenBrandIdMissing() {
        request.setBrandId(null);

        assertThatThrownBy(() -> productService.addProduct(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Brand is required");
    }

    @Test
    void addProduct_throwsWhenDuplicateNameForBrand() {
        when(brandRepository.findById(10L)).thenReturn(Optional.of(brand));
        when(productRepository.existsByNameAndBrand_Id("Vitamin C", 10L)).thenReturn(true);

        assertThatThrownBy(() -> productService.addProduct(request))
                .isInstanceOf(AlreadyExistsException.class);
    }

    @Test
    void addProduct_savesProductPublishesEventAndAssignsBarcode() {
        when(brandRepository.findById(10L)).thenReturn(Optional.of(brand));
        when(productRepository.existsByNameAndBrand_Id("Vitamin C", 10L)).thenReturn(false);
        when(productRepository.existsByBarcodeAndIdNot(any(), any())).thenReturn(false);

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(55L);
            }
            return p;
        });

        Product saved = productService.addProduct(request);

        assertThat(saved.getId()).isEqualTo(55L);
        assertThat(saved.getBarcode()).isEqualTo("SKM00000055");
        assertThat(saved.getCategory()).isEqualTo(brand.getCategory());

        verify(productRepository, times(2)).save(any(Product.class));
        verify(eventPublisher).publishEvent(any(ProductAddedEvent.class));
    }

    @Test
    void deleteProductById_throwsWhenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProductById(99L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void getProductByBarcode_normalizesAndLooksUp() {
        when(productRepository.findByBarcode("SKM00000001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductByBarcode("  skm00000001 "))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository).findByBarcode("SKM00000001");
    }

    @Test
    void toMarkdownTable_returnsPlaceholderWhenEmpty() {
        assertThat(productService.toMarkdownTable(List.of())).contains("No products available");
    }

    @Test
    void getActiveProducts_delegatesToRepository() {
        List<Product> list = List.of(new Product());
        when(productRepository.findByStatus(any())).thenReturn(list);

        assertThat(productService.getActiveProducts()).isSameAs(list);
    }
}
