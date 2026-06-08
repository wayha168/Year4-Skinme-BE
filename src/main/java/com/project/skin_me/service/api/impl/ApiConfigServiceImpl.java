package com.project.skin_me.service.api.impl;

import com.project.skin_me.model.config.ApiConfig;
import com.project.skin_me.repository.ApiConfigRepository;
import com.project.skin_me.service.api.ApiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiConfigServiceImpl implements ApiConfigService {

    private final ApiConfigRepository apiConfigRepository;

    @Override
    public ApiConfig createApiConfig(ApiConfig apiConfig) {
        return apiConfigRepository.save(apiConfig);
    }

    @Override
    public ApiConfig getApiConfigById(Long id) {
        return apiConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ApiConfig not found with id: " + id));
    }

    @Override
    public List<ApiConfig> getAllApiConfigs() {
        return apiConfigRepository.findAll();
    }

    @Override
    public ApiConfig updateApiConfig(Long id, ApiConfig apiConfig) {
        ApiConfig existing = apiConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ApiConfig not found with id: " + id));
        existing.setDomain(apiConfig.getDomain());
        existing.setApiKey(apiConfig.getApiKey());
        existing.setController(apiConfig.getController());
        existing.setEnabled(apiConfig.isEnabled());
        return apiConfigRepository.save(existing);
    }

    @Override
    public void deleteApiConfig(Long id) {
        ApiConfig existing = apiConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ApiConfig not found with id: " + id));
        apiConfigRepository.delete(existing);
    }
}