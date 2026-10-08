package com.docdebt.service;

import com.docdebt.service.LlmService.DualSummary;
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
 * OpenRouter service implementation using OpenAI-compatible chat completion API.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "docdebt.llm.provider", havingValue = "openrouter", matchIfMissing = true)
public class OpenRouterService implements LlmService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${docdebt.openrouter.api-key:}")
    private String apiKey;

    @Value("${docdebt.openrouter.fast-model:meta-llama/llama-3.3-70b-instruct}")
    private String fastModel;

    @Value("${docdebt.openrouter.power-model:anthropic/claude-3.5-sonnet}")
    private String powerModel;

    @Value("${docdebt.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String baseUrl;

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
        log.info(
                "OpenRouter configuration loaded: baseUrl={}, fastModel={}, powerModel={}, apiKeyConfigured={}",
                baseUrl,
                fastModel,
                powerModel,
                apiKey != null && !apiKey.isBlank()
        );
    }

    @Override
    public DualSummary summarizeDiff(String prTitle, String prBody, String diff) {
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

    @Override
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

    @Override
    public String synthesizeBusinessDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName) {
        String prompt = """
                You are updating the Business Functionality & Use Case document
                for the module "%s". Rewrite the document below to incorporate
                the non-technical changes described in the change log. Keep the
                language accessible to product managers and business stakeholders.
                Preserve existing structure/headings where possible. Output only the
                full updated document in Markdown, nothing else.

                === EXISTING DOCUMENT ===
                %s

                === BUSINESS CHANGE LOG ===
                %s
                """.formatted(moduleName, existingDoc, aggregatedSummaries);

        return generateContent(powerModel, prompt);
    }

    @Override
    public String scaffoldNewTechnicalDoc(String moduleName, String aggregatedSummaries) {
        String prompt = """
                You are creating an initial High-Level/Low-Level Design document
                for a new module named "%s". Generate a structured Markdown
                document with headings for Overview, Architecture & Data Flow,
                APIs & Data Models, Dependencies, and Key Behaviors, populated
                from the change log below. Output only the Markdown document,
                nothing else.

                === ENGINEERING CHANGE LOG ===
                %s
                """.formatted(moduleName, aggregatedSummaries);

        return generateContent(powerModel, prompt);
    }

    @Override
    public String scaffoldNewBusinessDoc(String moduleName, String aggregatedSummaries) {
        String prompt = """
                You are creating an initial Business Functionality & Use Case
                document for a new module named "%s". Generate a structured
                Markdown document with headings for Business Purpose, Key
                Capabilities & Use Cases, and User Impact, written for product
                managers and business stakeholders based on the change log below.
                Output only the Markdown document, nothing else.

                === BUSINESS CHANGE LOG ===
                %s
                """.formatted(moduleName, aggregatedSummaries);

        return generateContent(powerModel, prompt);
    }

    @Override
    public String analyzeImpact(String prompt) {
        return generateContent(fastModel, prompt);
    }

    @Override
    public float[] embed(String text) {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            return generateGeminiEmbedding(text);
        }
        log.warn("GEMINI_API_KEY not configured for embeddings - returning zero vector stub");
        return new float[768];
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
                throw new IllegalStateException("Gemini returned no embedding");
            }

            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            return vector;
        } catch (Exception ex) {
            log.error("Failed to generate embedding via Gemini", ex);
            return new float[768];
        }
    }

    private String generateContent(String model, String prompt) {
        String url = baseUrl + "/chat/completions";

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);

        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("Authorization", "Bearer " + apiKey.trim());
        }
        headers.set("HTTP-Referer", "https://github.com/pranjal-develops/self-healing-docs");
        headers.set("X-Title", "Doc-Debt Tracker");

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        long start = System.nanoTime();
        log.info("Sending OpenRouter generation request: model={}, promptLength={}, url={}", model, prompt == null ? 0 : prompt.length(), url);

        try {
            String responseJson = restTemplate.postForObject(url, entity, String.class);
            JsonNode response = mapper.readTree(responseJson);

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            JsonNode choice = response.path("choices").path(0);
            JsonNode messageNode = choice.path("message");

            String generatedText = messageNode.path("content").asText("");
            if (generatedText.isBlank() || "null".equals(generatedText)) {
                // Support reasoning / thinking models (e.g. Nemotron, DeepSeek R1, Qwen Reasoning)
                generatedText = messageNode.path("reasoning").asText("");
            }
            if (generatedText.isBlank() || "null".equals(generatedText)) {
                JsonNode reasoningDetails = messageNode.path("reasoning_details");
                if (reasoningDetails.isArray() && !reasoningDetails.isEmpty()) {
                    generatedText = reasoningDetails.get(0).path("text").asText("");
                }
            }

            if (generatedText.isBlank() || "null".equals(generatedText)) {
                log.error("OpenRouter returned no generated text: model={}, elapsedMs={}, response={}", model, elapsedMs, responseJson);
                throw new IllegalStateException("OpenRouter returned an empty response");
            }

            log.info("OpenRouter generation response received: model={}, responseLength={}, elapsedMs={}", model, generatedText.length(), elapsedMs);
            return generatedText;
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("OpenRouter 429 Rate Limit Exceeded (elapsedMs={}). Attempting automatic fallback to Gemini API...", elapsedMs);
            if (geminiApiKey != null && !geminiApiKey.isBlank()) {
                return generateGeminiFallback(prompt);
            }
            throw new RuntimeException("OpenRouter 429 Rate limit exceeded and GEMINI_API_KEY is not configured for fallback", ex);
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.error("OpenRouter generation request failed: model={}, elapsedMs={}, error={}", model, elapsedMs, ex.getMessage(), ex);
            throw new RuntimeException("OpenRouter API call failed: " + ex.getMessage(), ex);
        }
    }

    private String generateGeminiFallback(String prompt) {
        String model = "gemini-1.5-flash";
        String url = "%s/models/%s:generateContent".formatted(geminiBaseUrl, model);

        ObjectNode body = mapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode userContent = contents.addObject();
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey.trim());

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            log.info("Sending Gemini fallback generation request: model={}, url={}", model, url);
            String responseJson = restTemplate.postForObject(url, entity, String.class);
            JsonNode response = mapper.readTree(responseJson);

            String generatedText = response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (generatedText.isBlank()) {
                throw new IllegalStateException("Gemini fallback returned an empty response");
            }
            log.info("Gemini fallback response received successfully");
            return generatedText;
        } catch (Exception ex) {
            log.error("Gemini fallback generation failed", ex);
            throw new RuntimeException("Both OpenRouter (429) and Gemini fallback failed: " + ex.getMessage(), ex);
        }
    }

    private DualSummary parseDualSummary(String raw) {
        String tech = "";
        String bus = "";

        if (raw != null) {
            int techIdx = raw.indexOf("TECHNICAL:");
            int busIdx = raw.indexOf("BUSINESS:");

            if (techIdx != -1 && busIdx != -1 && busIdx > techIdx) {
                tech = raw.substring(techIdx + "TECHNICAL:".length(), busIdx).trim();
                bus = raw.substring(busIdx + "BUSINESS:".length()).trim();
            } else if (techIdx != -1) {
                tech = raw.substring(techIdx + "TECHNICAL:".length()).trim();
            } else {
                tech = raw.trim();
            }
        }

        if (tech.isBlank()) tech = "Updated codebase implementation details.";
        if (bus.isBlank()) bus = "No user-facing business impact.";

        return new DualSummary(tech, bus);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n... (truncated)";
    }
}
