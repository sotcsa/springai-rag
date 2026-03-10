package com.example.rag.service;

import com.example.rag.model.DocumentEntity;
import com.example.rag.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class DocumentProcessingAsyncService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingAsyncService.class);
    private static final int EMBEDDING_BATCH_SIZE = 25;

    private final DocumentRepository documentRepository;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;

    public DocumentProcessingAsyncService(DocumentRepository documentRepository,
            ChunkingService chunkingService,
            VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
    }

    /**
     * Async processing: parse -> chunk -> embed -> store in vector DB.
     */
    @Async("documentProcessingExecutor")
    public CompletableFuture<Void> processDocumentAsync(String docId, Path filePath, String fileName) {
        log.info("Starting async processing for document: {} ({})", fileName, docId);
        Instant startedAt = Instant.now();

        try {
            updateStatus(docId, DocumentEntity.ProcessingStatus.PROCESSING, null);

            List<Document> documents = parseDocument(filePath, fileName);
            log.info("Parsed {} page(s)/section(s) from {}", documents.size(), fileName);

            List<Document> chunks = chunkingService.chunkDocuments(documents);
            log.info("Created {} chunk(s) from {}", chunks.size(), fileName);

            updateChunkProgress(docId, 0);

            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("source_document", fileName);
                metadata.put("document_id", docId);
                metadata.put("chunk_index", i);
                metadata.put("total_chunks", chunks.size());
                chunks.set(i, new Document(chunk.getText(), metadata));
            }

            int processedChunks = 0;
            int totalChunks = chunks.size();
            for (int fromIndex = 0; fromIndex < totalChunks; fromIndex += EMBEDDING_BATCH_SIZE) {
                int toIndex = Math.min(fromIndex + EMBEDDING_BATCH_SIZE, totalChunks);
                List<Document> batch = new ArrayList<>(chunks.subList(fromIndex, toIndex));

                Instant batchStartedAt = Instant.now();
                vectorStore.add(batch);
                long batchMs = Duration.between(batchStartedAt, Instant.now()).toMillis();

                processedChunks = toIndex;
                updateChunkProgress(docId, processedChunks);
                log.info("Embedded batch for {}: {}/{} chunk(s), batchSize={}, elapsed={} ms",
                        fileName, processedChunks, totalChunks, batch.size(), batchMs);
            }

            log.info("Stored {} chunk(s) with embeddings for {}", totalChunks, fileName);

            DocumentEntity doc = documentRepository.findById(docId).orElseThrow();
            doc.setStatus(DocumentEntity.ProcessingStatus.COMPLETED);
            doc.setChunkCount(totalChunks);
            doc.setErrorMessage(null);
            documentRepository.save(doc);

            long totalMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.info("Finished async processing for {} ({}), total elapsed={} ms", fileName, docId, totalMs);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Error processing document {}: {}", docId, e.getMessage(), e);
            updateStatus(docId, DocumentEntity.ProcessingStatus.FAILED, e.getMessage());
            return CompletableFuture.failedFuture(e);
        } finally {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", filePath, e);
            }
        }
    }

    private List<Document> parseDocument(Path filePath, String fileName) {
        Resource resource = new org.springframework.core.io.FileSystemResource(filePath);

        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        if (documents.isEmpty() || documents.stream().allMatch(d -> d.getText().isBlank())) {
            try {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", fileName);
                    metadata.put("type", "plain_text");
                    documents = List.of(new Document(content, metadata));
                }
            } catch (IOException e) {
                log.warn("Could not read file as plain text: {}", e.getMessage());
            }
        }

        return documents;
    }

    private void updateStatus(String docId, DocumentEntity.ProcessingStatus status, String errorMessage) {
        documentRepository.findById(docId).ifPresent(doc -> {
            doc.setStatus(status);
            if (status == DocumentEntity.ProcessingStatus.PROCESSING) {
                doc.setChunkCount(0);
            }
            if (errorMessage != null) {
                doc.setErrorMessage(errorMessage.length() > 2000
                        ? errorMessage.substring(0, 2000)
                        : errorMessage);
            } else {
                doc.setErrorMessage(null);
            }
            documentRepository.save(doc);
        });
    }

    private void updateChunkProgress(String docId, int processedChunks) {
        documentRepository.findById(docId).ifPresent(doc -> {
            doc.setChunkCount(processedChunks);
            documentRepository.save(doc);
        });
    }
}

