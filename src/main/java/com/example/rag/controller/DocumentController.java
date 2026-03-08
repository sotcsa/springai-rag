package com.example.rag.controller;

import com.example.rag.model.DocumentEntity;
import com.example.rag.model.UploadResponse;
import com.example.rag.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Show upload page.
     */
    @GetMapping
    public String uploadPage(Model model) {
        List<DocumentEntity> documents = documentService.getAllDocuments();
        model.addAttribute("documents", documents);
        return "upload";
    }

    /**
     * Handle file upload via HTMX.
     */
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Receiving file upload: {}, size: {} bytes",
                    file.getOriginalFilename(), file.getSize());

            UploadResponse response = documentService.uploadDocument(file);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    UploadResponse.builder()
                            .status("ERROR")
                            .message(e.getMessage())
                            .build());
        } catch (IOException e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    UploadResponse.builder()
                            .status("ERROR")
                            .message("Hiba történt a fájl feltöltése közben: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get document status (for polling).
     */
    @GetMapping("/{id}/status")
    @ResponseBody
    public ResponseEntity<DocumentEntity> getDocumentStatus(@PathVariable String id) {
        DocumentEntity doc = documentService.getDocument(id);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(doc);
    }

    /**
     * HTMX fragment: document list.
     */
    @GetMapping("/list")
    public String documentList(Model model) {
        List<DocumentEntity> documents = documentService.getAllDocuments();
        model.addAttribute("documents", documents);
        return "fragments/document-list :: document-list";
    }

    /**
     * Delete a document.
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok().build();
    }
}
