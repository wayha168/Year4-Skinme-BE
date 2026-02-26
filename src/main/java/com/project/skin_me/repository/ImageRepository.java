package com.project.skin_me.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.skin_me.model.Image;

public interface ImageRepository extends JpaRepository<Image, Long> {

    List<Image> findByProductId(Long id);

    List<Image> findByProduct_IdIn(List<Long> productIds);

    java.util.Optional<Image> findFirstByFileName(String fileName);

}
