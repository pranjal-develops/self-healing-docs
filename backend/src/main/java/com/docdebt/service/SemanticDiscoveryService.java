package com.docdebt.service;

import com.docdebt.entity.CodeModule;
import com.docdebt.repository.ModuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Minimal in-memory vector index for the hackathon demo: embeddings are
 * stored as comma-separated strings on the CodeModule entity (one per doc
 * type) and compared via cosine similarity. Swap for pgvector/Pinecone once
 * this graduates past the demo.
 */
@Service
public class SemanticDiscoveryService {

    private final ModuleRepository moduleRepository;
    private final GeminiService geminiService;

    @Value("${docdebt.semantic.similarity-threshold}")
    private double similarityThreshold;

    public SemanticDiscoveryService(ModuleRepository moduleRepository, GeminiService geminiService) {
        this.moduleRepository = moduleRepository;
        this.geminiService = geminiService;
    }

    public record MatchResult(Optional<CodeModule> match, double bestScore) {}

    /** Finds the best-matching existing module doc (of the given type) for the given text. */
    public MatchResult findBestMatch(DocType type, String aggregatedText) {
        float[] queryVec = geminiService.embed(aggregatedText);

        List<CodeModule> candidates = moduleRepository.findAll().stream()
                .filter(m -> embeddingOf(m, type) != null && !embeddingOf(m, type).isBlank())
                .collect(Collectors.toList());

        CodeModule best = null;
        double bestScore = -1;
        for (CodeModule m : candidates) {
            float[] vec = parseEmbedding(embeddingOf(m, type));
            double score = cosineSimilarity(queryVec, vec);
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }

        if (best != null && bestScore >= similarityThreshold) {
            return new MatchResult(Optional.of(best), bestScore);
        }
        return new MatchResult(Optional.empty(), bestScore);
    }

    public void storeEmbedding(DocType type, CodeModule module, String textToEmbed) {
        float[] vec = geminiService.embed(textToEmbed);
        String serialized = serializeEmbedding(vec);
        if (type == DocType.TECHNICAL) {
            module.setTechnicalEmbedding(serialized);
        } else {
            module.setBusinessEmbedding(serialized);
        }
        moduleRepository.save(module);
    }

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
