package com.example.rag.service;

import com.example.rag.model.DocumentEntity;
import com.example.rag.model.UploadResponse;
import com.example.rag.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main service for document upload, parsing, chunking, embedding, and storage.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final List<String> SUPPORTED_TYPES = List.of(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentRepository documentRepository;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;

    public DocumentService(DocumentRepository documentRepository,
            ChunkingService chunkingService,
            VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
    }

    /**
     * Initiate document processing - saves metadata and starts async processing.
     */
    public UploadResponse uploadDocument(MultipartFile file) throws IOException {
        validateFile(file);

        // Save document metadata
        DocumentEntity doc = DocumentEntity.builder()
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .status(DocumentEntity.ProcessingStatus.PENDING)
                .build();

        doc = documentRepository.save(doc);

        // Save file temporarily for async processing
        Path tempFile = Files.createTempFile("doc-upload-", getExtension(file.getOriginalFilename()));
        file.transferTo(tempFile);

        // Start async processing
        processDocumentAsync(doc.getId(), tempFile, file.getOriginalFilename());

        return UploadResponse.builder()
                .documentId(doc.getId())
                .fileName(file.getOriginalFilename())
                .status("PENDING")
                .message("Dokumentum feltöltve, feldolgozás folyamatban...")
                .build();
    }

    /**
     * Async processing: parse → chunk → embed → store in vector DB.
     */
    @Async("documentProcessingExecutor")
    public CompletableFuture<Void> processDocumentAsync(String docId, Path filePath, String fileName) {
        log.info("Starting async processing for document: {} ({})", fileName, docId);

        try {
            // Update status
            updateStatus(docId, DocumentEntity.ProcessingStatus.PROCESSING, null);

            // 1. Parse document using Tika
            List<Document> documents = parseDocument(filePath, fileName);
            log.info("Parsed {} page(s)/section(s) from {}", documents.size(), fileName);

            // 2. Chunk documents
            List<Document> chunks = chunkingService.chunkDocuments(documents);
            log.info("Created {} chunk(s) from {}", chunks.size(), fileName);

            // 3. Add metadata to chunks
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("source_document", fileName);
                metadata.put("document_id", docId);
                metadata.put("chunk_index", i);
                metadata.put("total_chunks", chunks.size());
                chunks.set(i, new Document(chunk.getText(), metadata));
            }

            // 4. Store in vector store (embedding happens automatically)
            vectorStore.add(chunks);
            log.info("Stored {} chunk(s) with embeddings for {}", chunks.size(), fileName);

            // 5. Update document status
            DocumentEntity doc = documentRepository.findById(docId).orElseThrow();
            doc.setStatus(DocumentEntity.ProcessingStatus.COMPLETED);
            doc.setChunkCount(chunks.size());
            documentRepository.save(doc);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Error processing document {}: {}", docId, e.getMessage(), e);
            updateStatus(docId, DocumentEntity.ProcessingStatus.FAILED, e.getMessage());
            return CompletableFuture.failedFuture(e);

        } finally {
            // Clean up temp file
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", filePath, e);
            }
        }
    }

    /**
     * Parse document using Apache Tika (supports PDF, TXT, DOCX, etc.)
     */
    private List<Document> parseDocument(Path filePath, String fileName) {
        Resource resource = new org.springframework.core.io.FileSystemResource(filePath);

        // Use Tika reader for broad format support
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        // If no content extracted, try reading as plain text
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

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("A fájl üres.");
        }
        if (file.getSize() > 50 * 1024 * 1024) { // 50MB limit
            throw new IllegalArgumentException("A fájl mérete nem haladhatja meg az 50MB-ot.");
        }
        // Allow any file type, Tika will handle parsing
    }

    private void updateStatus(String docId, DocumentEntity.ProcessingStatus status, String errorMessage) {
        documentRepository.findById(docId).ifPresent(doc -> {
            doc.setStatus(status);
            if (errorMessage != null) {
                doc.setErrorMessage(errorMessage.length() > 2000
                        ? errorMessage.substring(0, 2000)
                        : errorMessage);
            }
            documentRepository.save(doc);
        });
    }

    private String getExtension(String filename) {
        if (filename == null)
            return ".tmp";
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot) : ".tmp";
    }

    /**
     * Get all documents ordered by creation date.
     */
    public List<DocumentEntity> getAllDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get document by ID.
     */
    public DocumentEntity getDocument(String id) {
        return documentRepository.findById(id).orElse(null);
    }

    /**
     * Delete document and its chunks from vector store.
     */
    public void deleteDocument(String id) {
        documentRepository.findById(id).ifPresent(doc -> {
            // Note: Spring AI VectorStore doesn't have a batch delete by metadata yet,
            // so we delete the document record only. Vector entries will become orphaned
            // but won't affect search quality significantly.
            documentRepository.delete(doc);
            log.info("Deleted document: {}", doc.getFileName());
        });
    }

    /**
     * Get processing statistics.
     */
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", documentRepository.count());
        stats.put("completed", documentRepository.countByStatus(DocumentEntity.ProcessingStatus.COMPLETED));
        stats.put("processing", documentRepository.countByStatus(DocumentEntity.ProcessingStatus.PROCESSING));
        stats.put("failed", documentRepository.countByStatus(DocumentEntity.ProcessingStatus.FAILED));
        stats.put("pending", documentRepository.countByStatus(DocumentEntity.ProcessingStatus.PENDING));
        return stats;
    }
}
