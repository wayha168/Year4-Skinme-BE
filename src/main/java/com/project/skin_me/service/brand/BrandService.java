package com.project.skin_me.service.brand;

import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Brand;
import com.project.skin_me.model.Category;
import com.project.skin_me.repository.BrandRepository;
import com.project.skin_me.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService implements IBrandService {

    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public Brand getBrandById(Long id) {
        return brandRepository.findByIdWithCategory(id)
                .orElse(brandRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id)));
    }

    @Override
    public Brand getBrandByNameAndCategoryId(String name, Long categoryId) {
        return brandRepository.findByNameAndCategoryId(name, categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + name + " in category " + categoryId));
    }

    @Override
    public List<Brand> getAllBrands() {
        return brandRepository.findAllWithCategory();
    }

    @Override
    public List<Brand> getBrandsByCategoryId(Long categoryId) {
        return brandRepository.findByCategoryId(categoryId);
    }

    @Override
    public List<Brand> getBrandsByCategoryName(String categoryName) {
        return brandRepository.findByCategory_Name(categoryName);
    }

    @Override
    @Transactional
    public Brand createBrand(String name, String imageUrl, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        Brand brand = new Brand(name, imageUrl != null ? imageUrl : "", category);
        return brandRepository.save(brand);
    }

    @Override
    @Transactional
    public Brand updateBrand(Long id, String name, String imageUrl) {
        Brand brand = getBrandById(id);
        if (name != null) brand.setName(name);
        if (imageUrl != null) brand.setImageUrl(imageUrl);
        return brandRepository.save(brand);
    }

    @Override
    @Transactional
    public void deleteBrandById(Long id) {
        brandRepository.findById(id)
                .ifPresentOrElse(brandRepository::delete,
                        () -> { throw new ResourceNotFoundException("Brand not found: " + id); });
    }
}
