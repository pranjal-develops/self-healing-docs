package com.docdebt.service;

import com.docdebt.entity.CodeModule;
import com.docdebt.repository.ModuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Minimal in-memory vector index: embeddings are stored as comma-separated
 * strings on the CodeModule entity (one per doc type) and compared via cosine
 * similarity. Swap for pgvector/Pinecone once this graduates past the demo.
 *
 * Key fix: if the LLM embedding call fails, findBestMatch() returns an empty
 * MatchResult instead of matching against a zero-vector (which would produce
 * a misleading false-positive match).
 */
@Slf4j
@Service
public class SemanticDiscoveryService {

    private final ModuleRepository moduleRepository;
    private final LlmService llmService;

    @Value("${docdebt.semantic.similarity-threshold:0.85}")
    private double similarityThreshold;

    public SemanticDiscoveryService(ModuleRepository moduleRepository, LlmService llmService) {
        this.moduleRepository = moduleRepository;
        this.llmService = llmService;
    }

    public record MatchResult(Optional<CodeModule> match, double bestScore) {}

    /**
     * Finds the best-matching existing module doc (of the given type) for the
     * given text. Returns an empty result if embeddings are unavailable or if
     * no module crosses the similarity threshold.
     */
    public MatchResult findBestMatch(DocType type, String aggregatedText) {
        float[] queryVec;
        try {
            queryVec = llmService.embed(aggregatedText);
        } catch (Exception e) {
            log.warn("Embedding unavailable – skipping semantic discovery ({})", e.getMessage());
            return new MatchResult(Optional.empty(), -1);
        }

        // Sanity check: reject zero-vector (all zeros = uninformative)
        if (isZeroVector(queryVec)) {
            log.warn("Embedding returned a zero-vector – skipping semantic discovery");
            return new MatchResult(Optional.empty(), -1);
        }

        List<CodeModule> candidates = moduleRepository.findAll().stream()
                .filter(m -> embeddingOf(m, type) != null && !embeddingOf(m, type).isBlank())
                .collect(Collectors.toList());

        CodeModule best = null;
        double bestScore = -1;
        for (CodeModule m : candidates) {
            try {
                float[] vec = parseEmbedding(embeddingOf(m, type));
                if (isZeroVector(vec)) continue; // skip stored zero-vectors
                double score = cosineSimilarity(queryVec, vec);
                if (score > bestScore) {
                    bestScore = score;
                    best = m;
                }
            } catch (Exception e) {
                log.warn("Failed to parse embedding for module {}: {}", m.getName(), e.getMessage());
            }
        }

        if (best != null && bestScore >= similarityThreshold) {
            return new MatchResult(Optional.of(best), bestScore);
        }
        return new MatchResult(Optional.empty(), bestScore);
    }

    /**
     * Computes and persists an embedding for the given module's doc.
     * If embedding fails, logs a warning and leaves the existing value intact.
     */
    public void storeEmbedding(DocType type, CodeModule module, String textToEmbed) {
        try {
            float[] vec = llmService.embed(textToEmbed);
            if (isZeroVector(vec)) {
                log.warn("Skipping zero-vector embedding storage for module {}", module.getName());
                return;
            }
            String serialized = serializeEmbedding(vec);
            if (type == DocType.TECHNICAL) {
                module.setTechnicalEmbedding(serialized);
            } else {
                module.setBusinessEmbedding(serialized);
            }
            moduleRepository.save(module);
        } catch (Exception e) {
            log.warn("Embedding storage skipped for module {} ({}): embeddings will not improve semantic discovery",
                    module.getName(), e.getMessage());
        }
    }

    // ---- Private helpers ---------------------------------------------------

    private String embeddingOf(CodeModule m, DocType type) {
        return type == DocType.TECHNICAL ? m.getTechnicalEmbedding() : m.getBusinessEmbedding();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return -1;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private boolean isZeroVector(float[] vec) {
        if (vec == null || vec.length == 0) return true;
        for (float v : vec) {
            if (v != 0f) return false;
        }
        return true;
    }

    private String serializeEmbedding(float[] vec) {
        StringBuilder sb = new StringBuilder();
        for (float v : vec) sb.append(v).append(",");
        return sb.toString();
    }

    private float[] parseEmbedding(String s) {
        String[] parts = s.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isBlank()) vec[i] = Float.parseFloat(parts[i]);
        }
        return vec;
    }
}
