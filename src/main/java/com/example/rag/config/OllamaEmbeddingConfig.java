package com.example.rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Ollama embedding model.
 * This provides the EmbeddingModel bean required by VectorStoreConfig.
 */
@Configuration
public class OllamaEmbeddingConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    @Value("${spring.ai.ollama.embedding.options.model}")
    private String model;

    @Bean
    public EmbeddingModel embeddingModel() {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        OllamaOptions options = OllamaOptions.builder()
                .model(model)
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
    }
}
