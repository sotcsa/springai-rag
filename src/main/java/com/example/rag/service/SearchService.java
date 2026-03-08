package com.example.rag.service;

import com.example.rag.model.SearchRequest;
import com.example.rag.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for semantic search and RAG (Retrieval-Augmented Generation).
 * Supports two modes:
 * 1. CHUNKS_ONLY - returns matching document chunks
 * 2. RAG - uses LLM to generate an answer based on retrieved chunks
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    private static final String RAG_PROMPT_TEMPLATE = """
            Te egy segítőkész asszisztens vagy. A feladatod, hogy a megadott kontextus alapján válaszolj a felhasználó kérdésére.

            SZABÁLYOK:
            - Csak a megadott kontextus alapján válaszolj
            - Ha a kontextus nem tartalmaz elegendő információt, jelezd ezt
            - Hivatkozz a forrás dokumentumokra ahol lehetséges
            - Válaszolj magyarul, ha a kérdés magyarul van, egyébként az adott nyelven
            - Legyél precíz és informatív

            KONTEXTUS:
            {context}

            KÉRDÉS: {query}

            VÁLASZ:
            """;

    public SearchService(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * Perform semantic search with the specified mode.
     */
    public SearchResult search(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Searching: query='{}', mode={}, topK={}",
                request.getQuery(), request.getMode(), request.getTopK());

        // 1. Perform vector similarity search
        List<Document> relevantDocs = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(request.getQuery())
                        .topK(request.getTopK())
                        .build());

        log.info("Found {} relevant chunk(s)", relevantDocs.size());

        // 2. Convert to chunk results
        List<SearchResult.ChunkResult> chunkResults = relevantDocs.stream()
                .map(doc -> SearchResult.ChunkResult.builder()
                        .content(doc.getText())
                        .documentName(getDocumentName(doc))
                        .score(getScore(doc))
                        .metadata(doc.getMetadata())
                        .build())
                .collect(Collectors.toList());

        // 3. If RAG mode, generate LLM answer
        String llmAnswer = null;
        if (request.getMode() == SearchRequest.SearchMode.RAG && !relevantDocs.isEmpty()) {
            llmAnswer = generateRagAnswer(request.getQuery(), relevantDocs);
        }

        long searchTimeMs = System.currentTimeMillis() - startTime;

        return SearchResult.builder()
                .query(request.getQuery())
                .mode(request.getMode())
                .llmAnswer(llmAnswer)
                .chunks(chunkResults)
                .searchTimeMs(searchTimeMs)
                .build();
    }

    /**
     * Generate an answer using LLM based on retrieved context.
     */
    private String generateRagAnswer(String query, List<Document> relevantDocs) {
        log.info("Generating RAG answer with {} context chunk(s)...", relevantDocs.size());

        try {
            // Build context from relevant documents
            String context = relevantDocs.stream()
                    .map(doc -> {
                        String source = getDocumentName(doc);
                        return String.format("[Forrás: %s]\n%s", source, doc.getText());
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            // Call LLM
            String answer = chatClient.prompt()
                    .user(RAG_PROMPT_TEMPLATE
                            .replace("{context}", context)
                            .replace("{query}", query))
                    .call()
                    .content();

            log.info("RAG answer generated successfully");
            return answer;

        } catch (Exception e) {
            log.error("Failed to generate RAG answer: {}", e.getMessage(), e);
            return "Hiba történt az LLM válasz generálása közben: " + e.getMessage();
        }
    }

    private String getDocumentName(Document doc) {
        Object source = doc.getMetadata().get("source_document");
        if (source != null)
            return source.toString();

        source = doc.getMetadata().get("source");
        if (source != null)
            return source.toString();

        return "Ismeretlen dokumentum";
    }

    private double getScore(Document doc) {
        Object score = doc.getMetadata().get("distance");
        if (score instanceof Number) {
            return ((Number) score).doubleValue();
        }
        return 0.0;
    }
}
