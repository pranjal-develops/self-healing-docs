package com.docdebt.service;

/**
 * Unified LLM abstraction. All prompt templates live here to guarantee
 * consistent output quality regardless of the provider (Gemini / OpenRouter).
 */
public interface LlmService {

    record DualSummary(String technicalSummary, String businessSummary) {}

    // ---- Map phase ----

    /**
     * Summarise a single merged PR diff into a technical + business dual-summary.
     * The diff is treated as untrusted data and is wrapped in XML delimiters
     * to prevent prompt injection.
     */
    DualSummary summarizeDiff(String prTitle, String prBody, String diff);

    // ---- Reduce phase ----

    String synthesizeTechnicalDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName);
    String synthesizeBusinessDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName);

    // ---- Scaffold phase ----

    String scaffoldNewTechnicalDoc(String moduleName, String aggregatedSummaries);
    String scaffoldNewBusinessDoc(String moduleName, String aggregatedSummaries);

    // ---- Impact analysis ----

    String analyzeImpact(String prompt);

    // ---- Embeddings ----

    /**
     * Returns an embedding vector for the given text.
     * Throws {@link IllegalStateException} if the embedding cannot be produced —
     * callers must handle this and skip semantic matching rather than silently
     * storing a zero-vector.
     */
    float[] embed(String text);

    // ---- Shared prompt templates (called by both provider implementations) ----

    static String buildSummarizeDiffPrompt(String prTitle, String prBody, String budgetedDiff) {
        // PR data is wrapped in XML-style delimiters to prevent prompt injection.
        return """
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

                <pr_title>%s</pr_title>
                <pr_description>%s</pr_description>

                <diff>
                %s
                </diff>
                """.formatted(
                sanitize(prTitle),
                sanitize(prBody == null ? "(none)" : prBody),
                budgetedDiff
        );
    }

    static String buildSynthesizeTechnicalPrompt(String moduleName, String existingDoc, String aggregatedSummaries) {
        return """
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
                """.formatted(sanitize(moduleName), existingDoc, aggregatedSummaries);
    }

    static String buildSynthesizeBusinessPrompt(String moduleName, String existingDoc, String aggregatedSummaries) {
        return """
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
                """.formatted(sanitize(moduleName), existingDoc, aggregatedSummaries);
    }

    static String buildScaffoldTechnicalPrompt(String moduleName, String aggregatedSummaries) {
        return """
                Generate a brand-new architectural design document for a module
                called "%s", based only on the change log below. Use this template
                with these exact headings: Overview, System Architecture, Endpoints,
                Data Model, Dependencies. Output only the Markdown document.

                === ENGINEERING CHANGE LOG ===
                %s
                """.formatted(sanitize(moduleName), aggregatedSummaries);
    }

    static String buildScaffoldBusinessPrompt(String moduleName, String aggregatedSummaries) {
        return """
                Generate a brand-new business/feature document for a module called
                "%s", based only on the change log below. Use this template with
                these exact headings: Overview, Key Features, Use Cases,
                Stakeholders, Business Value. Write for a non-technical audience -
                no implementation detail. If the change log is entirely internal
                changes with no user-facing impact, say so plainly under Overview
                instead of inventing features. Output only the Markdown document.

                === FEATURE/USE-CASE CHANGE LOG ===
                %s
                """.formatted(sanitize(moduleName), aggregatedSummaries);
    }

    /**
     * Basic sanitization to remove XML-breaking characters from user-supplied
     * metadata fields (title, description). The diff itself is already enclosed
     * in delimiters and truncated, so this is for the short fields only.
     */
    private static String sanitize(String input) {
        if (input == null) return "";
        return input.replace("<", "[").replace(">", "]").replace("&", "&amp;");
    }

    // ---- Diff budget utilities ----

    /**
     * Patterns for files that contribute no useful signal to the LLM:
     * lock files, generated code, binaries (by extension pattern).
     */
    static boolean isDiffFileSkippable(String filePath) {
        if (filePath == null) return false;
        String lower = filePath.toLowerCase();
        return lower.endsWith("package-lock.json")
                || lower.endsWith("yarn.lock")
                || lower.endsWith("pnpm-lock.yaml")
                || lower.endsWith("poetry.lock")
                || lower.endsWith("gemfile.lock")
                || lower.endsWith(".min.js")
                || lower.endsWith(".min.css")
                || lower.endsWith(".map")
                || lower.endsWith(".pb.go")
                || lower.endsWith(".pb.cc")
                || lower.endsWith(".pb.h")
                || lower.endsWith("_generated.go")
                || lower.endsWith(".generated.ts")
                || lower.endsWith(".snap")          // snapshot files
                || lower.endsWith(".svg")
                || lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".ico")
                || lower.endsWith(".woff")
                || lower.endsWith(".woff2")
                || lower.endsWith(".ttf")
                || lower.endsWith(".eot");
    }

    /**
     * Splits a unified diff by file, drops skippable files, then gives each
     * remaining file an equal share of {@code totalBudget} characters.
     * Returns the assembled, budget-capped diff string.
     */
    static String budgetDiff(String diff, int totalBudget) {
        if (diff == null || diff.isBlank()) return "";

        // Split on "diff --git" boundaries
        String[] chunks = diff.split("(?=diff --git )");

        // Filter skippable files
        java.util.List<String> kept = new java.util.ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.isBlank()) continue;
            // Extract file path from "diff --git a/path b/path"
            String header = chunk.split("\n")[0];
            String filePath = header.contains(" b/") ? header.substring(header.lastIndexOf(" b/") + 3) : "";
            if (!isDiffFileSkippable(filePath)) {
                kept.add(chunk);
            }
        }

        if (kept.isEmpty()) return "(all changed files were lock/generated/binary files — no diff content)";

        int perFile = Math.max(500, totalBudget / kept.size());
        StringBuilder sb = new StringBuilder(totalBudget + 256);
        for (String chunk : kept) {
            if (chunk.length() <= perFile) {
                sb.append(chunk);
            } else {
                sb.append(chunk, 0, perFile).append("\n... [file diff truncated]\n");
            }
        }
        return sb.toString();
    }
}
