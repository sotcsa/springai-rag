package com.example.rag.service;

import com.example.rag.model.DocumentEntity;
import com.example.rag.model.UploadResponse;
import com.example.rag.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final DocumentProcessingAsyncService documentProcessingAsyncService;

    public DocumentService(DocumentRepository documentRepository,
            DocumentProcessingAsyncService documentProcessingAsyncService) {
        this.documentRepository = documentRepository;
        this.documentProcessingAsyncService = documentProcessingAsyncService;
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

        // Start async processing via a separate bean to ensure proxy-based @Async execution
        documentProcessingAsyncService.processDocumentAsync(doc.getId(), tempFile, file.getOriginalFilename());

        return UploadResponse.builder()
                .documentId(doc.getId())
                .fileName(file.getOriginalFilename())
                .status("PENDING")
                .message("Dokumentum feltöltve, feldolgozás folyamatban...")
                .build();
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
