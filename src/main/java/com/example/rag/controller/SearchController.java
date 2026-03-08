package com.example.rag.controller;

import com.example.rag.model.SearchRequest;
import com.example.rag.model.SearchResult;
import com.example.rag.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Show search page.
     */
    @GetMapping
    public String searchPage() {
        return "search";
    }

    /**
     * Perform search - returns results fragment for HTMX.
     */
    @PostMapping
    public String performSearch(
            @RequestParam("query") String query,
            @RequestParam(value = "mode", defaultValue = "CHUNKS_ONLY") String mode,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            Model model) {

        log.info("Search request: query='{}', mode={}, topK={}", query, mode, topK);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .mode(SearchRequest.SearchMode.valueOf(mode))
                .topK(topK)
                .build();

        SearchResult result = searchService.search(request);
        model.addAttribute("result", result);

        return "fragments/search-results :: search-results";
    }

    /**
     * REST API endpoint for search.
     */
    @PostMapping("/api")
    @ResponseBody
    public SearchResult searchApi(@RequestBody SearchRequest request) {
        log.info("API Search request: query='{}', mode={}, topK={}",
                request.getQuery(), request.getMode(), request.getTopK());

        return searchService.search(request);
    }
}
