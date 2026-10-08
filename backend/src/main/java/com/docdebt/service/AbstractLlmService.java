package com.docdebt.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Shared retry/backoff logic for both Gemini and OpenRouter providers.
 * Retries on 429 (rate-limit) and 5xx (transient server errors) with
 * exponential backoff up to {@code maxRetries} attempts.
 */
@Slf4j
abstract class AbstractLlmService implements LlmService {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1_500;

    // ---- Retry-wrapped generation ------------------------------------------

    protected String generateWithRetry(String model, String prompt) {
        int attempt = 0;
        while (true) {
            try {
                return doGenerate(model, prompt);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (attempt >= MAX_RETRIES) {
                    log.error("Rate-limit (429) exceeded after {} retries for model={}", MAX_RETRIES, model);
                    throw new RuntimeException("LLM rate-limit exceeded: " + ex.getMessage(), ex);
                }
                long delay = BASE_DELAY_MS * (1L << attempt);   // 1.5s, 3s, 6s
                log.warn("429 rate-limit on attempt {}/{} for model={} – retrying in {}ms", attempt + 1, MAX_RETRIES, model, delay);
                sleep(delay);
            } catch (HttpServerErrorException ex) {
                if (attempt >= MAX_RETRIES) {
                    log.error("Server error ({}) after {} retries for model={}", ex.getStatusCode(), MAX_RETRIES, model);
                    throw new RuntimeException("LLM server error: " + ex.getMessage(), ex);
                }
                long delay = BASE_DELAY_MS * (1L << attempt);
                log.warn("{}  on attempt {}/{} for model={} – retrying in {}ms", ex.getStatusCode(), attempt + 1, MAX_RETRIES, model, delay);
                sleep(delay);
            }
            attempt++;
        }
    }

    // ---- Prompt delegation -------------------------------------------------

    @Override
    public DualSummary summarizeDiff(String prTitle, String prBody, String diff) {
        String budgeted = LlmService.budgetDiff(diff, 12_000);
        String prompt = LlmService.buildSummarizeDiffPrompt(prTitle, prBody, budgeted);
        String raw = generateWithRetry(getFastModel(), prompt);
        return parseDualSummary(raw);
    }

    @Override
    public String synthesizeTechnicalDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName) {
        String prompt = LlmService.buildSynthesizeTechnicalPrompt(moduleName, existingDoc, aggregatedSummaries);
        return generateWithRetry(getPowerModel(), prompt);
    }

    @Override
    public String synthesizeBusinessDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName) {
        String prompt = LlmService.buildSynthesizeBusinessPrompt(moduleName, existingDoc, aggregatedSummaries);
        return generateWithRetry(getPowerModel(), prompt);
    }

    @Override
    public String scaffoldNewTechnicalDoc(String moduleName, String aggregatedSummaries) {
        String prompt = LlmService.buildScaffoldTechnicalPrompt(moduleName, aggregatedSummaries);
        return generateWithRetry(getPowerModel(), prompt);
    }

    @Override
    public String scaffoldNewBusinessDoc(String moduleName, String aggregatedSummaries) {
        String prompt = LlmService.buildScaffoldBusinessPrompt(moduleName, aggregatedSummaries);
        return generateWithRetry(getPowerModel(), prompt);
    }

    @Override
    public String analyzeImpact(String prompt) {
        return generateWithRetry(getFastModel(), prompt);
    }

    // ---- Template methods --------------------------------------------------

    /** Provider-specific HTTP call; should throw Spring HttpStatusCodeException on error. */
    protected abstract String doGenerate(String model, String prompt);

    protected abstract String getFastModel();
    protected abstract String getPowerModel();

    // ---- Shared parsing ----------------------------------------------------

    protected DualSummary parseDualSummary(String raw) {
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

    // ---- Utilities ---------------------------------------------------------

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
