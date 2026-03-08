package com.example.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for splitting documents into smaller chunks suitable for embedding.
 * Uses token-based splitting with overlap for better context preservation.
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    private final TokenTextSplitter textSplitter;

    public ChunkingService(
            @Value("${app.chunking.chunk-size:800}") int chunkSize,
            @Value("${app.chunking.chunk-overlap:200}") int chunkOverlap,
            @Value("${app.chunking.min-chunk-size:100}") int minChunkSize) {

        this.textSplitter = new TokenTextSplitter(
                chunkSize, // default chunk size in tokens
                chunkOverlap, // overlap between chunks
                minChunkSize, // minimum chunk size
                10000, // max number of chunks
                true // keep separator
        );

        log.info("ChunkingService initialized: chunkSize={}, overlap={}, minSize={}",
                chunkSize, chunkOverlap, minChunkSize);
    }

    /**
     * Split documents into smaller chunks with metadata preserved.
     */
    public List<Document> chunkDocuments(List<Document> documents) {
        log.debug("Chunking {} document(s)...", documents.size());

        List<Document> chunks = textSplitter.apply(documents);

        log.info("Split {} document(s) into {} chunk(s)", documents.size(), chunks.size());
        return chunks;
    }
}
