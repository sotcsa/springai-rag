package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    private String query;

    @Builder.Default
    private SearchMode mode = SearchMode.CHUNKS_ONLY;

    @Builder.Default
    private int topK = 5;

    public enum SearchMode {
        CHUNKS_ONLY,
        RAG
    }
}
