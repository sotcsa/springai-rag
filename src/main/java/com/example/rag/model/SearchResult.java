package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String query;
    private SearchRequest.SearchMode mode;
    private String llmAnswer;
    private List<ChunkResult> chunks;
    private long searchTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkResult {
        private String content;
        private String documentName;
        private double score;
        private Map<String, Object> metadata;
    }
}
