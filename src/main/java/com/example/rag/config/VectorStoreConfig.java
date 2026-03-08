package com.example.rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore.OracleVectorStoreDistanceType;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore.OracleVectorStoreIndexType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Bean
    public OracleVectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return OracleVectorStore.builder(jdbcTemplate, embeddingModel)
                .tableName("VECTOR_STORE")
                .indexType(OracleVectorStoreIndexType.IVF)
                .distanceType(OracleVectorStoreDistanceType.COSINE)
                .dimensions(768) // nomic-embed-text dimension
                .searchAccuracy(95)
                .initializeSchema(true)
                .build();
    }
}
