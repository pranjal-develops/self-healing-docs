package com.docdebt.service;

import com.docdebt.entity.CodeModule;
import com.docdebt.repository.ModuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImpactAnalysisService {

    private final LlmService llmService;
    private final ModuleRepository moduleRepository;

    public ImpactAnalysisService(LlmService llmService, ModuleRepository moduleRepository) {
        this.llmService = llmService;
        this.moduleRepository = moduleRepository;
    }

    public record ImpactResult(
            Set<String> affectedModules,
            Map<String, List<String>> docUpdatesNeeded
    ) {}

    public ImpactResult analyzeImpact(String prTitle, String prBody, String diff) {
        List<CodeModule> allModules = moduleRepository.findAll();

        if (allModules.isEmpty()) {
            log.warn("No modules found in database - returning empty impact analysis");
            return new ImpactResult(Set.of(), Map.of());
        }

        String modulesList = allModules.stream()
                .map(CodeModule::getName)
                .collect(Collectors.joining(", "));

        String prompt = buildImpactPrompt(prTitle, prBody, diff, modulesList);
        String analysis = llmService.analyzeImpact(prompt);

        return parseImpactResult(analysis, allModules);
    }

    private String buildImpactPrompt(String prTitle, String prBody, String diff, String modulesList) {
        return """
                Analyze this PR and identify which modules it affects and what documentation needs updating.

                PR Title: %s
                PR Description: %s

                Changed Files Diff:
                %s

                Available Modules: %s

                Return a JSON response with this exact structure:
                {
                  "affectedModules": ["module1", "module2"],
                  "docUpdates": {
                    "module1": ["technical doc needs update for X", "business doc needs update for Y"],
                    "module2": ["technical doc needs update for Z"]
                  }
                }

                Rules:
                1. Only include modules that are in the available modules list
                2. For each affected module, specify if technical and/or business docs need updates
                3. Be specific about what needs updating
                4. If a module is not in the list, do not include it
                """.formatted(prTitle, prBody != null ? prBody : "No description", truncateDiff(diff), modulesList);
    }

    private String truncateDiff(String diff) {
        if (diff == null) return "";
        int maxChars = 8000;
        if (diff.length() <= maxChars) return diff;
        return diff.substring(0, maxChars) + "\n... (truncated)";
    }

    private ImpactResult parseImpactResult(String analysis, List<CodeModule> allModules) {
        try {
            Set<String> moduleNames = allModules.stream()
                    .map(CodeModule::getName)
                    .collect(Collectors.toSet());

            Map<String, Object> json = parseJson(analysis);
            @SuppressWarnings("unchecked")
            List<String> affectedModulesRaw = (List<String>) json.getOrDefault("affectedModules", List.of());
            @SuppressWarnings("unchecked")
            Map<String, List<String>> docUpdatesRaw = (Map<String, List<String>>) json.getOrDefault("docUpdates", Map.of());

            Set<String> affectedModules = affectedModulesRaw.stream()
                    .filter(moduleNames::contains)
                    .collect(Collectors.toSet());

            Map<String, List<String>> docUpdates = docUpdatesRaw.entrySet().stream()
                    .filter(e -> moduleNames.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            return new ImpactResult(affectedModules, docUpdates);
        } catch (Exception e) {
            log.error("Failed to parse impact analysis result, falling back to simple module inference", e);
            return fallbackAnalysis(allModules);
        }
    }

    private Map<String, Object> parseJson(String json) {
        json = json.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        }
        if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        // If LLM includes code snippets in diff output containing braces, locate the target JSON object
        int candidateStart = json.indexOf("{\n  \"affectedModules\"");
        if (candidateStart == -1) candidateStart = json.indexOf("{\"affectedModules\"");
        if (candidateStart == -1) candidateStart = json.indexOf('{');

        int candidateEnd = json.lastIndexOf('}');
        if (candidateStart != -1 && candidateEnd != -1 && candidateEnd > candidateStart) {
            json = json.substring(candidateStart, candidateEnd + 1);
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON parsing failed for content: " + json, e);
        }
    }

    private ImpactResult fallbackAnalysis(List<CodeModule> allModules) {
        if (allModules.isEmpty()) {
            return new ImpactResult(Set.of(), Map.of());
        }
        CodeModule firstModule = allModules.get(0);
        return new ImpactResult(
                Set.of(firstModule.getName()),
                Map.of(firstModule.getName(), List.of("technical doc needs review", "business doc needs review"))
        );
    }
}
