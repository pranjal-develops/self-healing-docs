package com.docdebt.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * OpenRouter (OpenAI-compatible) chat completion implementation.
 * Activated when docdebt.llm.provider=openrouter (the default).
 * Embeddings are delegated to Gemini if a Gemini API key is present.
 * All prompt templates and retry logic are inherited from AbstractLlmService.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "docdebt.llm.provider", havingValue = "openrouter", matchIfMissing = true)
public class OpenRouterService extends AbstractLlmService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${docdebt.openrouter.api-key:}")
    private String apiKey;

    @Value("${docdebt.openrouter.fast-model:nvidia/nemotron-3-super-120b-a12b:free}")
    private String fastModel;

    @Value("${docdebt.openrouter.power-model:nvidia/nemotron-3-super-120b-a12b:free}")
    private String powerModel;

    @Value("${docdebt.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String baseUrl;

    // Embeddings are not available on OpenRouter, so we fall back to Gemini.
    @Value("${docdebt.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${docdebt.gemini.embedding-model:gemini-embedding-001}")
    private String geminiEmbeddingModel;

    @Value("${docdebt.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    public OpenRouterService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    void logConfiguration() {
        log.info("OpenRouter provider active: baseUrl={}, fastModel={}, powerModel={}, apiKeyConfigured={}, geminiEmbeddings={}",
                baseUrl, fastModel, powerModel,
                apiKey != null && !apiKey.isBlank(),
                geminiApiKey != null && !geminiApiKey.isBlank());
    }

    // ---- AbstractLlmService hooks ----------------------------------------

    @Override
    protected String getFastModel() { return fastModel; }

    @Override
    protected String getPowerModel() { return powerModel; }

    @Override
    protected String doGenerate(String model, String prompt) {
        String url = baseUrl + "/chat/completions";

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("Authorization", "Bearer " + apiKey.trim());
        }
        headers.set("HTTP-Referer", "https://github.com/pranjal-develops/self-healing-docs");
        headers.set("X-Title", "Doc-Debt Tracker");

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        long start = System.nanoTime();
        log.debug("OpenRouter generate: model={}, promptLen={}", model, prompt.length());

        try {
            String responseJson = restTemplate.postForObject(url, entity, String.class);
            JsonNode response = mapper.readTree(responseJson);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            JsonNode choice = response.path("choices").path(0);
            JsonNode messageNode = choice.path("message");

            String text = messageNode.path("content").asText("");
            if (text.isBlank() || "null".equals(text)) {
                // Support reasoning/thinking models (DeepSeek R1, Nemotron, Qwen…)
                text = messageNode.path("reasoning").asText("");
            }
            if (text.isBlank() || "null".equals(text)) {
                JsonNode details = messageNode.path("reasoning_details");
                if (details.isArray() && !details.isEmpty()) {
                    text = details.get(0).path("text").asText("");
                }
            }

            if (text.isBlank() || "null".equals(text)) {
                log.error("OpenRouter returned no text: model={}, elapsedMs={}, response={}", model, elapsedMs, responseJson);
                throw new IllegalStateException("OpenRouter returned an empty response for model=" + model);
            }

            log.info("OpenRouter response: model={}, responseLen={}, elapsedMs={}", model, text.length(), elapsedMs);
            return text;

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse OpenRouter response", ex);
        }
        // HTTP 429 and 5xx propagate as Spring HttpStatusCodeException → caught by AbstractLlmService.generateWithRetry
    }

    // ---- Embeddings (delegated to Gemini) ----------------------------------

    @Override
    public float[] embed(String text) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Embeddings are not available: set GEMINI_API_KEY to enable semantic document discovery when using OpenRouter.");
        }
        return generateGeminiEmbedding(text);
    }

    private float[] generateGeminiEmbedding(String text) {
        String url = "%s/models/%s:embedContent".formatted(geminiBaseUrl, geminiEmbeddingModel);

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "models/" + geminiEmbeddingModel);
        ObjectNode content = body.putObject("content");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", text == null ? "" : text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey.trim());

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            String responseJson = restTemplate.postForObject(url, entity, String.class);
            JsonNode response = mapper.readTree(responseJson);
            JsonNode values = response.path("embedding").path("values");
            if (!values.isArray() || values.isEmpty()) {
                throw new IllegalStateException("Gemini embedding returned no vector");
            }
            float[] vec = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vec[i] = (float) values.get(i).asDouble();
            }
            return vec;
        } catch (Exception ex) {
            log.error("Gemini embedding (from OpenRouter fallback) failed: {}", ex.getMessage(), ex);
            // Never silently return a zero vector — let the caller decide.
            throw new IllegalStateException("Embedding via Gemini failed: " + ex.getMessage(), ex);
        }
    }
}
