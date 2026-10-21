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
                You are a Senior Software Architect updating the technical design document (HLD/LLD) for the module "%s".

                INSTRUCTIONS:
                1. Rewrite the architectural sections to incorporate all new engineering changes, modified APIs, updated data models, and component additions described in the change log.
                2. Preserve existing document structure, accurate sections, and established technical details.
                3. STRICT ANTI-HALLUCINATION RULE: Do NOT invent or hallucinate non-existent files, class names, API endpoints, or data models that are not present in the provided context, existing doc, or diffs. If details are missing, write "N/A — Not specified in repository context" rather than making up fictitious classes or files.
                4. Output ONLY the complete updated Markdown document, nothing else.

                === EXISTING TECHNICAL DESIGN DOCUMENT ===
                %s

                === RECENT ENGINEERING CHANGES & CODE DIFFERENCES ===
                %s
                """.formatted(sanitize(moduleName), existingDoc, aggregatedSummaries);
    }

    static String buildSynthesizeBusinessPrompt(String moduleName, String existingDoc, String aggregatedSummaries) {
        return """
                You are updating the business and product documentation for the module "%s".

                INSTRUCTIONS:
                1. Rewrite the Features, Workflows, and User Impact sections to incorporate feature/use-case changes described in the change log.
                2. Write for a non-technical stakeholder (product manager / business executive) — explain what the software does, key business rules, and user impact without implementation code.
                3. STRICT ANTI-HALLUCINATION RULE: Focus strictly on actual features and user impact described in the provided context. Do NOT invent non-existent features or module capabilities.
                4. Preserve existing accurate feature descriptions and headings where possible.
                5. Output ONLY the complete updated Markdown document, nothing else.

                === EXISTING BUSINESS DOCUMENT ===
                %s

                === FEATURE & USE-CASE CHANGE LOG ===
                %s
                """.formatted(sanitize(moduleName), existingDoc, aggregatedSummaries);
    }

    static String buildScaffoldTechnicalPrompt(String moduleName, String aggregatedSummaries) {
        return """
                You are a Principal Software Architect writing a production-grade High-Level/Low-Level Design (HLD/LLD) document for the software module "%s".

                CRITICAL REQUIREMENTS:
                1. STRICT ANTI-HALLUCINATION RULE: Do NOT invent or hallucinate non-existent file names, class names, API endpoints, or data models that are not explicitly present in the provided codebase context or README. If specific details (e.g. database schemas or specific API endpoints) are not present in the context, write "N/A — Not specified in repository context" under that section instead of inventing fictitious examples (such as PaymentController, OrderService, etc.).
                2. Base your design document strictly on the actual code context, build files, and engineering changes provided below.
                3. Format cleanly using GitHub-flavored Markdown.
                4. Output ONLY the Markdown document, nothing else.

                Include these exact section headings:
                # Technical Design Document: %s

                ## 1. Executive Summary & Core Responsibilities
                High-level architecture, module scope, and primary technical capabilities.

                ## 2. Component Architecture & System Flow
                Key service classes, controllers, schedulers, and how data moves through the module.

                ## 3. API Contracts & Endpoint Specifications
                List HTTP methods, path routes, request headers, parameters, payload schemas, and response formats.

                ## 4. Data Model, Database Schema & Entities
                Document entities, database tables, column names, data types, index constraints, and relationships.

                ## 5. Integrations & External Dependencies
                List third-party APIs, libraries, databases, and external service contracts used by this module.

                === CODEBASE CONTEXT & ENGINEERING CHANGE LOG ===
                %s
                """.formatted(sanitize(moduleName), sanitize(moduleName), aggregatedSummaries);
    }

    static String buildScaffoldBusinessPrompt(String moduleName, String aggregatedSummaries) {
        return """
                You are a Product Manager writing a comprehensive business and feature specification document for the module "%s".

                CRITICAL REQUIREMENTS:
                1. Write for non-technical stakeholders (Product Managers, Executives, Support teams).
                2. STRICT ANTI-HALLUCINATION RULE: Focus strictly on actual product features and business capabilities described in the provided context. Do NOT invent non-existent features, workflows, or files not present in the codebase context.
                3. Do NOT include code snippets or internal implementation details.
                4. Output ONLY the Markdown document, nothing else.

                Include these exact section headings:
                # Business Feature Specification: %s

                ## 1. Executive Summary
                High-level business purpose and value proposition of the module.

                ## 2. Key Business Features & Capabilities
                Detailed breakdown of user-facing capabilities and business functionality.

                ## 3. Workflow & Business Rules
                Step-by-step user/business workflows and domain constraints.

                ## 4. Target Stakeholders & Value Delivered
                Who benefits from this module (End-Users, Developers, Admins) and key business outcomes.

                === FEATURE & USE-CASE CONTEXT ===
                %s
                """.formatted(sanitize(moduleName), sanitize(moduleName), aggregatedSummaries);
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
