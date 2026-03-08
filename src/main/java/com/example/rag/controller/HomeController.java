package com.example.rag.controller;

import com.example.rag.model.DocumentEntity;
import com.example.rag.service.DocumentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final DocumentService documentService;

    public HomeController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/")
    public String home(Model model) {
        Map<String, Long> stats = documentService.getStats();
        List<DocumentEntity> recentDocs = documentService.getAllDocuments();

        model.addAttribute("stats", stats);
        model.addAttribute("documents", recentDocs);
        return "index";
    }
}
