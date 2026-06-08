package com.project.skin_me.service.api;

import com.project.skin_me.model.config.ApiConfig;
import java.util.List;

public interface ApiConfigService {

    ApiConfig createApiConfig(ApiConfig apiConfig);

    ApiConfig getApiConfigById(Long id);

    List<ApiConfig> getAllApiConfigs();

    ApiConfig updateApiConfig(Long id, ApiConfig apiConfig);

    void deleteApiConfig(Long id);
}