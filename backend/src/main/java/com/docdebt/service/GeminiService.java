package com.docdebt.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Wraps the Gemini generateContent + embedContent REST endpoints.
 * Docs: https://ai.google.dev/api/generate-content
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "docdebt.llm.provider", havingValue = "gemini")
public class GeminiService implements LlmService {

//
//    private static final Logger log =
//            LoggerFactory.getLogger(GeminiService.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${docdebt.gemini.api-key}")
    private String apiKey;

    @Value("${docdebt.gemini.fast-model}")
    private String fastModel;

    @Value("${docdebt.gemini.power-model}")
    private String powerModel;

    @Value("${docdebt.gemini.embedding-model}")
    private String embeddingModel;

    @Value("${docdebt.gemini.base-url}")
    private String baseUrl;

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Map phase: from a single code diff, produce both:
     *  - a two-sentence engineering summary (endpoints, data model, deps, behavior)
     *  - a plain-language feature/use-case summary for a business audience,
     *    or an explicit "no user-facing impact" note for internal-only changes.
     */
    @Override
    public LlmService.DualSummary summarizeDiff(String prTitle, String prBody, String diff) {
        String prompt = """
                Analyze this merged pull request and return EXACTLY two labeled
                sections, nothing else:

                TECHNICAL: Two sentences, engineering-focused. Be concrete about
                what architecturally changed - new/changed endpoints, data model
                changes, new dependencies, altered internal behavior.

                BUSINESS: One or two sentences, written for a non-technical
                stakeholder, describing any new feature, use case, or user-facing
                behavior change this PR introduces. If this PR is purely internal
                (refactor, dependency bump, test, infra) with no user-facing
                effect, write exactly: "No user-facing business impact."

                PR Title: %s
                PR Description: %s

                Diff:
                %s
                """.formatted(prTitle, prBody == null ? "(none)" : prBody, truncate(diff, 12000));

        String raw = generateContent(fastModel, prompt);
        return parseDualSummary(raw);
    }

    /** Reduce phase (technical doc): rewrite architectural sections given accumulated changes. */
    public String synthesizeTechnicalDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName) {
        String prompt = """
                You are updating the High-Level/Low-Level Design document for the
                module "%s". Rewrite the architectural sections of the document
                below to incorporate the historical engineering changes described
                in the change log, while preserving sections that are still
                accurate. Keep the existing structure/headings where possible.
                Output only the full updated document in Markdown, nothing else.

                === EXISTING DOCUMENT ===
                %s

                === ENGINEERING CHANGE LOG ===
                %s
                """.formatted(moduleName, existingDoc, aggregatedSummaries);
        return generateContent(powerModel, prompt);
    }

    /** Reduce phase (business doc): rewrite feature/use-case sections given accumulated changes. */
    public String synthesizeBusinessDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName) {
        String prompt = """
                You are updating the business/feature documentation for the module
                "%s". Rewrite the Features and Use Cases sections of the document
                below to incorporate the feature/use-case changes described in the
                change log, while preserving sections that are still accurate.
                Write for a non-technical stakeholder - no implementation detail,
                no code, no endpoint names. If a change log entry has no
                user-facing impact, do not add it as a feature. Keep the existing
                structure/headings where possible. Output only the full updated
                document in Markdown, nothing else.

                === EXISTING DOCUMENT ===
                %s

                === FEATURE/USE-CASE CHANGE LOG ===
                %s
                """.formatted(moduleName, existingDoc, aggregatedSummaries);
        return generateContent(powerModel, prompt);
    }

    /** Auto-scaffolding (technical): generate a brand-new HLD/LLD doc from scratch. */
    public String scaffoldNewTechnicalDoc(String moduleName, String aggregatedSummaries) {
        String prompt = """
                Generate a brand-new architectural design document for a module
                called "%s", based only on the change log below. Use this template
                with these exact headings: Overview, System Architecture, Endpoints,
                Data Model, Dependencies. Output only the Markdown document.

                === ENGINEERING CHANGE LOG ===
                %s
                """.formatted(moduleName, aggregatedSummaries);
        return generateContent(powerModel, prompt);
    }

    /** Auto-scaffolding (business): generate a brand-new feature/use-case doc from scratch. */
    public String scaffoldNewBusinessDoc(String moduleName, String aggregatedSummaries) {
        String prompt = """
                Generate a brand-new business/feature document for a module called
                "%s", based only on the change log below. Use this template with
                these exact headings: Overview, Key Features, Use Cases,
                Stakeholders, Business Value. Write for a non-technical audience -
                no implementation detail. If the change log is entirely internal
                changes with no user-facing impact, say so plainly under Overview
                instead of inventing features. Output only the Markdown document.

                === FEATURE/USE-CASE CHANGE LOG ===
                %s
                """.formatted(moduleName, aggregatedSummaries);
        return generateContent(powerModel, prompt);
    }

    /** Impact analysis: determine which modules are affected and what docs need updates */
    public String analyzeImpact(String prompt) {
        return generateContent(powerModel, prompt);
    }

    /** Returns an embedding vector for the given text (used for semantic discovery). */
//    public float[] embed(String text) {
//        String url = "%s/models/%s:embedContent?key=%s".formatted(baseUrl, embeddingModel, apiKey);
//
//        ObjectNode body = mapper.createObjectNode();
//        ObjectNode content = body.putObject("content");
//        ArrayNode parts = content.putArray("parts");
//        parts.addObject().put("text", truncate(text, 8000));
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
//
//        JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
//        JsonNode values = response.path("embedding").path("values");
//        float[] vec = new float[values.size()];
//        for (int i = 0; i < values.size(); i++) {
//            vec[i] = (float) values.get(i).asDouble();
//        }
//        return vec;
//    }

    public float[] embed(String text) {
        String url = "%s/models/%s:embedContent"
                .formatted(baseUrl, embeddingModel);

        ObjectNode body = mapper.createObjectNode();
        ObjectNode content = body.putObject("content");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", truncate(text, 8000));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey.trim());

        HttpEntity<String> entity =
                new HttpEntity<>(body.toString(), headers);

        long start = System.nanoTime();

        log.info(
                "Sending Gemini embedding request: model={}, textLength={}, url={}",
                embeddingModel,
                text == null ? 0 : text.length(),
                url
        );

        try {
            String responseJson =
                    restTemplate.postForObject(url, entity, String.class);
            JsonNode response = mapper.readTree(responseJson);

            long elapsedMs =
                    (System.nanoTime() - start) / 1_000_000;

            JsonNode values =
                    response.path("embedding").path("values");

            if (!values.isArray() || values.isEmpty()) {
                log.error(
                        "Gemini embedding response contained no vector: elapsedMs={}, response={}",
                        elapsedMs,
                        responseJson
                );

                throw new IllegalStateException(
                        "Gemini returned no embedding");
            }

            log.info(
                    "Gemini embedding response received: model={}, vectorDimensions={}, elapsedMs={}",
                    embeddingModel,
                    values.size(),
                    elapsedMs
            );

            float[] vector = new float[values.size()];

            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }

            return vector;

        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Gemini embedding response", ex);
            throw new IllegalStateException("Failed to parse Gemini response", ex);
        } catch (Exception ex) {
            long elapsedMs =
                    (System.nanoTime() - start) / 1_000_000;

            log.error(
                    "Gemini embedding request failed: model={}, elapsedMs={}, error={}",
                    embeddingModel,
                    elapsedMs,
                    ex.getMessage(),
                    ex
            );

            throw ex;
        }
    }

    private LlmService.DualSummary parseDualSummary(String raw) {
        String technical = extractSection(raw, "TECHNICAL:", "BUSINESS:");
        String business = extractSection(raw, "BUSINESS:", null);
        return new LlmService.DualSummary(
                technical.isBlank() ? raw.trim() : technical,
                business.isBlank() ? "No user-facing business impact." : business
        );
    }

    private String extractSection(String raw, String startMarker, String endMarker) {
        int start = raw.indexOf(startMarker);
        if (start < 0) return "";
        start += startMarker.length();
        int end = endMarker == null ? raw.length() : raw.indexOf(endMarker, start);
        if (end < 0) end = raw.length();
        return raw.substring(start, end).trim();
    }

//    private String generateContent(String model, String prompt) {
//        String url = "%s/models/%s:generateContent?key=%s".formatted(baseUrl, model, apiKey);
//
//        ObjectNode body = mapper.createObjectNode();
//        ArrayNode contents = body.putArray("contents");
//        ObjectNode userContent = contents.addObject();
//        ArrayNode parts = userContent.putArray("parts");
//        parts.addObject().put("text", prompt);
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
//
//        JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
//        return response
//                .path("candidates").path(0)
//                .path("content").path("parts").path(0)
//                .path("text").asText("");
//    }

    private String generateContent(String model, String prompt) {
        String url = "%s/models/%s:generateContent"
                .formatted(baseUrl, model);

        ObjectNode body = mapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");

        ObjectNode userContent = contents.addObject();
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey.trim());

        HttpEntity<String> entity =
                new HttpEntity<>(body.toString(), headers);

        long start = System.nanoTime();

        log.info(
                "Sending Gemini generation request: model={}, promptLength={}, url={}",
                model,
                prompt == null ? 0 : prompt.length(),
                url
        );

        try {
            String responseJson =
                    restTemplate.postForObject(url, entity, String.class);
            JsonNode response = mapper.readTree(responseJson);

            long elapsedMs =
                    (System.nanoTime() - start) / 1_000_000;

            JsonNode candidates = response.path("candidates");
            String generatedText = response
                    .path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");

            if (generatedText.isBlank()) {
                log.error(
                        "Gemini returned no generated text: model={}, elapsedMs={}, candidatesPresent={}, response={}",
                        model,
                        elapsedMs,
                        candidates.isArray() && !candidates.isEmpty(),
                        responseJson
                );

                throw new IllegalStateException(
                        "Gemini returned an empty response");
            }

            log.info(
                    "Gemini generation response received: model={}, responseLength={}, elapsedMs={}",
                    model,
                    generatedText.length(),
                    elapsedMs
            );

            return generatedText;

        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Gemini generation response", ex);
            throw new IllegalStateException("Failed to parse Gemini response", ex);
        } catch (Exception ex) {
            long elapsedMs =
                    (System.nanoTime() - start) / 1_000_000;

            log.error(
                    "Gemini generation request failed: model={}, elapsedMs={}, error={}",
                    model,
                    elapsedMs,
                    ex.getMessage(),
                    ex
            );

            throw ex;
        }
    }


    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "\n...[truncated]" : s;
    }

    @PostConstruct
    void logGeminiConfiguration() {
        log.info(
                "Gemini configuration loaded: baseUrl={}, fastModel={}, powerModel={}, embeddingModel={}, apiKeyConfigured={}",
                baseUrl,
                fastModel,
                powerModel,
                embeddingModel,
                apiKey != null && !apiKey.isBlank()
        );
    }
}
