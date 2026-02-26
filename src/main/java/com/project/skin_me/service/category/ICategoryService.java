package com.project.skin_me.service.category;

import java.util.List;

import com.project.skin_me.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ICategoryService {
    Category getCategoryById(Long id);

    Category getCategoryByName(String name);

    List<Category> getAllCategories();

    /** Paginated: only fetches the requested page from DB. */
    Page<Category> getAllCategories(Pageable pageable);

    Category addCategory(Category category);

    Category updateCategory(Category category, Long id);

    Category deleteCategoryById(Long id);

}
