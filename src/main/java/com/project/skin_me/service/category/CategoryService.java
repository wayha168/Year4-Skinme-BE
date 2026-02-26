package com.project.skin_me.service.category;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.project.skin_me.model.Category;
import com.project.skin_me.exception.AlreadyExistsException;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService implements ICategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public Category getCategoryById(Long id) {
        Category category = categoryRepository.findByIdWithBrands(id);
        if (category != null) {
            return category;
        }
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not Success!"));
    }

    @Override
    public Category getCategoryByName(String name) {
        return categoryRepository.findByname(name);
    }

    @Override
    public List<Category> getAllCategories() {
        // Use optimized query only when product count is needed
        return categoryRepository.findAll();
    }

    @Override
    public Page<Category> getAllCategories(Pageable pageable) {
        return categoryRepository.findAllWithBrands(pageable);
    }

    public List<Category> getAllCategoriesWithBrands() {
        return categoryRepository.findAllWithBrands();
    }

    @Override
    public Category addCategory(Category category) {
        if (category.getImage() != null && category.getImage().length() > IMAGE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Image URL must be at most " + IMAGE_MAX_LENGTH + " characters. Create the category first, then use POST /categories/category/{id}/image to upload a file.");
        }
        return Optional.of(category).filter(c -> !categoryRepository.existsByName(c.getName()))
                .map(categoryRepository::save)
                .orElseThrow(() -> new AlreadyExistsException(category.getName() + "already exists"));
    }

    /** Max length for category image field (URL or path). */
    public static final int IMAGE_MAX_LENGTH = 2048;

    @Override
    public Category updateCategory(Category category, Long id) {
        return Optional.ofNullable(getCategoryById(id)).map(oldCategory -> {
            if (category.getName() != null) {
                oldCategory.setName(category.getName());
            }
            if (category.getTitle() != null) {
                oldCategory.setTitle(category.getTitle());
            }
            if (category.getDescription() != null) {
                oldCategory.setDescription(category.getDescription());
            }
            if (category.getLink() != null) {
                oldCategory.setLink(category.getLink());
            }
            if (category.getImage() != null) {
                if (category.getImage().length() > IMAGE_MAX_LENGTH) {
                    throw new IllegalArgumentException(
                            "Image URL must be at most " + IMAGE_MAX_LENGTH + " characters. Use POST /categories/category/{id}/image to upload a file.");
                }
                oldCategory.setImage(category.getImage());
            }
            return categoryRepository.save(oldCategory);
        }).orElseThrow(() -> new ResourceNotFoundException("Category not Success!"));
    }

    @Override
    public Category deleteCategoryById(Long id) {
        categoryRepository.findById(id)
                .ifPresentOrElse(categoryRepository::delete,
                        () -> {
                            throw new ResourceNotFoundException("Category not Success!");
                        });
        return null;
    }

}
