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
 * Gemini generateContent + embedContent implementation.
 * Activated when docdebt.llm.provider=gemini.
 * All prompt templates and retry logic are inherited from AbstractLlmService.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "docdebt.llm.provider", havingValue = "gemini")
public class GeminiService extends AbstractLlmService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${docdebt.gemini.api-key}")
    private String apiKey;

    @Value("${docdebt.gemini.fast-model:gemini-1.5-flash}")
    private String fastModel;

    @Value("${docdebt.gemini.power-model:gemini-1.5-pro}")
    private String powerModel;

    @Value("${docdebt.gemini.embedding-model:gemini-embedding-001}")
    private String embeddingModel;

    @Value("${docdebt.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    void logConfiguration() {
        log.info("Gemini provider active: baseUrl={}, fastModel={}, powerModel={}, embeddingModel={}, apiKeyConfigured={}",
                baseUrl, fastModel, powerModel, embeddingModel, apiKey != null && !apiKey.isBlank());
    }

    // ---- AbstractLlmService hooks ----------------------------------------

    @Override
    protected String getFastModel() {
        return fastModel;
    }

    @Override
    protected String getPowerModel() {
        return powerModel;
    }

    @Override
    protected String doGenerate(String model, String prompt) {
        String url = "%s/models/%s:generateContent".formatted(baseUrl, model);

        ObjectNode body = mapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode userContent = contents.addObject();
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey.trim());

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        long start = System.nanoTime();
        log.debug("Gemini generate: model={}, promptLen={}", model, prompt.length());

        try {
            String responseJson = restTemplate.postForObject(url, entity, String.class);
            JsonNode response = mapper.readTree(responseJson);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            JsonNode candidates = response.path("candidates");
            String text = candidates.path(0).path("content").path("parts").path(0).path("text").asText("");

            if (text.isBlank()) {
                log.error("Gemini returned no text: model={}, elapsedMs={}, response={}", model, elapsedMs, responseJson);
                throw new IllegalStateException("Gemini returned an empty response for model=" + model);
            }

            log.info("Gemini response: model={}, responseLen={}, elapsedMs={}", model, text.length(), elapsedMs);
            return text;

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse Gemini response", ex);
        }
        // HTTP errors (429, 5xx) propagate as Spring HttpStatusCodeException → caught by AbstractLlmService.generateWithRetry
    }

    // ---- Embeddings --------------------------------------------------------

    @Override
    public float[] embed(String text) {
        String url = "%s/models/%s:embedContent".formatted(baseUrl, embeddingModel);

        ObjectNode body = mapper.createObjectNode();
        ObjectNode content = body.putObject("content");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", text == null ? "" : text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey.trim());

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        long start = System.nanoTime();
        try {
            String responseJson = restTemplate.postForObject(url, entity, String.class);
            JsonNode response = mapper.readTree(responseJson);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            JsonNode values = response.path("embedding").path("values");
            if (!values.isArray() || values.isEmpty()) {
                log.error("Gemini embedding empty: elapsedMs={}, response={}", elapsedMs, responseJson);
                throw new IllegalStateException("Gemini returned no embedding vector");
            }

            float[] vec = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vec[i] = (float) values.get(i).asDouble();
            }
            log.info("Gemini embedding: model={}, dims={}, elapsedMs={}", embeddingModel, vec.length, elapsedMs);
            return vec;

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse Gemini embedding response", ex);
        } catch (Exception ex) {
            log.error("Gemini embedding failed: {}", ex.getMessage(), ex);
            throw ex; // Never return a zero-vector — callers must handle the exception
        }
    }
}
