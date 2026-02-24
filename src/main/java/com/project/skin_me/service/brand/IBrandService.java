package com.project.skin_me.service.brand;

import com.project.skin_me.model.Brand;

import java.util.List;

public interface IBrandService {

    Brand getBrandById(Long id);

    Brand getBrandByNameAndCategoryId(String name, Long categoryId);

    List<Brand> getAllBrands();

    List<Brand> getBrandsByCategoryId(Long categoryId);

    List<Brand> getBrandsByCategoryName(String categoryName);

    Brand createBrand(String name, String imageUrl, Long categoryId);

    Brand updateBrand(Long id, String name, String imageUrl);

    void deleteBrandById(Long id);
}
