package com.example.mpdemo.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 配置
 * 支持 NVIDIA API (OpenAI 兼容格式)
 */
@Slf4j
@Component
public class AIConfig {

    @Value("${nvidia.api.key:}")
    private String apiKey;

    @Value("${nvidia.api.base-url:https://integrate.api.nvidia.com/v1}")
    private String baseUrl;

    @Value("${nvidia.model:nvidia/nemotron-3-ultra-550b-a55b}")
    private String model;

    @Value("${nvidia.embedding-model:}")
    private String embeddingModel;

    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("NVIDIA API Key 未配置，AI 功能将不可用。请设置环境变量 NVIDIA_API_KEY");
        } else {
            log.info("AI 配置加载完成，模型: {}, 端点: {}", model, baseUrl);
        }
    }

    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
    public String getEmbeddingModel() { return embeddingModel; }
}
