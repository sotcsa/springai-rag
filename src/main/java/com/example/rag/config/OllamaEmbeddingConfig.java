package com.example.rag.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
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
        private String embeddingModel;

        @Value("${spring.ai.ollama.chat.options.model}")
        private String chatModel;

        @Value("${spring.ai.ollama.chat.options.temperature:0.3}")
        private double temperature;

        @Value("${spring.ai.ollama.chat.options.num-ctx:8192}")
        private int numCtx;

        @Bean
        public OllamaApi ollamaApi() {
                return OllamaApi.builder()
                                .baseUrl(baseUrl)
                                .build();
        }

        @Bean
        public EmbeddingModel embeddingModel(OllamaApi ollamaApi) {
                OllamaOptions options = OllamaOptions.builder()
                                .model(embeddingModel)
                                .build();

                return OllamaEmbeddingModel.builder()
                                .ollamaApi(ollamaApi)
                                .defaultOptions(options)
                                .build();
        }

        @Bean
        public ChatModel chatModel(OllamaApi ollamaApi) {
                OllamaOptions options = OllamaOptions.builder()
                                .model(chatModel)
                                .temperature(temperature)
                                .numCtx(numCtx)
                                .build();

                return OllamaChatModel.builder()
                                .ollamaApi(ollamaApi)
                                .defaultOptions(options)
                                .build();
        }
}
