package com.docdebt.service;

import com.docdebt.entity.CodeModule;
import com.docdebt.repository.ModuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Maps changed file paths from a PR diff to one or more module names.
 *
 * Strategy:
 * 1. Matches against existing CodeModules registered in the database for the repo.
 * 2. Skips standard build and package folder noise (src, main, java, com, org, etc.).
 * 3. Falls back to the repository's main module (or repository name) rather than creating
 *    spurious cards for Java package segments (like "Com", "Service", "Dto").
 */
@Slf4j
@Service
public class ModuleResolver {

    private final ModuleRepository moduleRepository;
    private final LlmService llmService;

    /**
     * Patterns for path segments that are build-tool or package noise rather than module names.
     */
    private static final Set<String> SKIP_SEGMENTS = Set.of(
            "src", "main", "java", "kotlin", "resources", "test",
            "lib", "libs", "pkg", "app", "apps", "packages",
            "backend", "frontend", "web", "api", "core", "common",
            "shared", "util", "utils", "internal",
            "com", "org", "net", "io", "dev", "gov", "edu",
            "service", "services", "controller", "controllers",
            "entity", "entities", "repository", "repositories",
            "dto", "dtos", "config", "component", "components",
            "model", "models", "impl", "helper", "helpers"
    );

    public ModuleResolver(ModuleRepository moduleRepository, LlmService llmService) {
        this.moduleRepository = moduleRepository;
        this.llmService = llmService;
    }

    public Map<String, List<String>> resolveModules(String diff) {
        return resolveModules(diff, null);
    }

    public Map<String, List<String>> resolveModules(String diff, String repoFull) {
        List<String> changedPaths = extractChangedPaths(diff);

        List<CodeModule> existingModules = moduleRepository.findAll();
        String fallbackModuleName = determineFallbackModuleName(repoFull, existingModules);

        if (changedPaths.isEmpty()) {
            return Map.of(fallbackModuleName, List.of());
        }

        // Build a lowercased module-name -> CodeModule map
        Map<String, CodeModule> byName = new LinkedHashMap<>();
        for (CodeModule m : existingModules) {
            byName.put(m.getName().toLowerCase(Locale.ROOT), m);
        }

        Map<String, List<String>> result = new LinkedHashMap<>();

        for (String path : changedPaths) {
            String moduleName = resolvePathToModule(path, byName, fallbackModuleName);
            result.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(path);
        }

        return result;
    }

    public String resolvePrimaryModule(String diff) {
        return resolvePrimaryModule(diff, null);
    }

    public String resolvePrimaryModule(String diff, String repoFull) {
        Map<String, List<String>> resolved = resolveModules(diff, repoFull);
        return resolved.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElseGet(() -> determineFallbackModuleName(repoFull, moduleRepository.findAll()));
    }

    private String determineFallbackModuleName(String repoFull, List<CodeModule> existingModules) {
        // 1. If repoFull is provided, look for an existing module registered for this repo
        if (repoFull != null && !repoFull.isBlank()) {
            for (CodeModule m : existingModules) {
                if (repoFull.equalsIgnoreCase(m.getRepositoryFullName())) {
                    return m.getName();
                }
            }
            // Extract short repo name (e.g. "self-healing-docs" from "pranjal-develops/self-healing-docs")
            String repoName = repoFull.contains("/") ? repoFull.substring(repoFull.lastIndexOf('/') + 1) : repoFull;
            for (CodeModule m : existingModules) {
                if (m.getName().equalsIgnoreCase(repoName)) {
                    return m.getName();
                }
            }
            return repoName;
        }

        // 2. If exactly one module exists in DB, use it as single-module fallback
        if (existingModules.size() == 1) {
            return existingModules.get(0).getName();
        }

        return "Unclassified";
    }

    private String resolvePathToModule(String path, Map<String, CodeModule> modulesByName, String fallbackModuleName) {
        String[] segments = path.split("[/\\\\]");
        for (String seg : segments) {
            if (seg.isBlank() || SKIP_SEGMENTS.contains(seg.toLowerCase(Locale.ROOT))) continue;

            String cleanSeg = seg.contains(".") ? seg.substring(0, seg.lastIndexOf('.')) : seg;
            String lower = cleanSeg.toLowerCase(Locale.ROOT);

            // Check exact match against registered module names
            if (modulesByName.containsKey(lower)) {
                return modulesByName.get(lower).getName();
            }

            // Check substring match against existing registered module names only
            for (Map.Entry<String, CodeModule> entry : modulesByName.entrySet()) {
                if (entry.getKey().startsWith(lower) || lower.startsWith(entry.getKey())) {
                    return entry.getValue().getName();
                }
            }
        }

        // Fall back to repo's primary module instead of inventing a random card name from code path!
        return fallbackModuleName;
    }

    private List<String> extractChangedPaths(String diff) {
        if (diff == null) return List.of();
        List<String> paths = new ArrayList<>();
        Pattern p = Pattern.compile("^diff --git a/(.*) b/", Pattern.MULTILINE);
        var matcher = p.matcher(diff);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (!LlmService.isDiffFileSkippable(path)) {
                paths.add(path);
            }
        }
        return paths;
    }
}
