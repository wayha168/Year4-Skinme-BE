package com.project.skin_me.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Ensures the upload directory exists at startup so that after a fresh pull
 * (when uploads/ is missing) the folder is created and the app does not fail.
 */
@Configuration
public class UploadDirConfig {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @PostConstruct
    public void createUploadDirIfMissing() {
        Path path = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + path, e);
        }
    }
}
