package com.docdebt.service;

import com.docdebt.entity.CodeModule;
import com.docdebt.repository.ModuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Maps changed file paths from a PR diff to one or more module names.
 *
 * Resolution strategy (in priority order):
 *
 * 1. Path-prefix mapping — check if any existing CodeModule's technicalDocPath
 *    or businessDocPath starts with a known directory. Also falls back to the
 *    module name used as a directory prefix (e.g., "payment" matches
 *    "src/payment/...").
 * 2. LLM fallback — if no static mapping resolves the path, ask the LLM to
 *    classify the changed paths against the list of known module names.
 * 3. "Unclassified" — if no modules exist or nothing matches, use a safe
 *    sentinel value.
 *
 * This completely replaces the old first-path-segment heuristic that always
 * returned "src" for Maven projects.
 */
@Slf4j
@Service
public class ModuleResolver {

    private final ModuleRepository moduleRepository;
    private final LlmService llmService;

    /**
     * Patterns for path segments that are build-tool noise rather than module
     * names. If the first meaningful segment matches one of these, we skip it
     * and look deeper.
     */
    private static final Set<String> SKIP_SEGMENTS = Set.of(
            "src", "main", "java", "kotlin", "resources", "test",
            "lib", "libs", "pkg", "app", "apps", "packages",
            "backend", "frontend", "web", "api", "core", "common",
            "shared", "util", "utils", "internal"
    );

    public ModuleResolver(ModuleRepository moduleRepository, LlmService llmService) {
        this.moduleRepository = moduleRepository;
        this.llmService = llmService;
    }

    /**
     * Given a unified diff string, returns a map of moduleName → list of
     * changed file paths belonging to that module.
     *
     * A PR touching multiple modules produces multiple entries so each module
     * gets its own summary built from its own files.
     */
    public Map<String, List<String>> resolveModules(String diff) {
        List<String> changedPaths = extractChangedPaths(diff);
        if (changedPaths.isEmpty()) {
            return Map.of("Unclassified", List.of());
        }

        List<CodeModule> existingModules = moduleRepository.findAll();

        // Build a lowercased module-name → module map for prefix matching
        Map<String, CodeModule> byName = new LinkedHashMap<>();
        for (CodeModule m : existingModules) {
            byName.put(m.getName().toLowerCase(Locale.ROOT), m);
        }

        Map<String, List<String>> result = new LinkedHashMap<>();

        for (String path : changedPaths) {
            String moduleName = resolvePathToModule(path, byName);
            result.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(path);
        }

        // If every path ended up "Unclassified" AND there are existing modules,
        // try LLM inference as a last resort.
        if (result.size() == 1 && result.containsKey("Unclassified") && !existingModules.isEmpty()) {
            String llmModuleName = inferWithLlm(changedPaths, existingModules);
            result = Map.of(llmModuleName, changedPaths);
        }

        return result;
    }

    /**
     * Simplified version that returns a single best-guess module name for
     * backward compatibility with code that only needs one module per PR.
     */
    public String resolvePrimaryModule(String diff) {
        Map<String, List<String>> resolved = resolveModules(diff);
        // Return the module with the most changed files
        return resolved.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse("Unclassified");
    }

    // ---- Private helpers ---------------------------------------------------

    private String resolvePathToModule(String path, Map<String, CodeModule> modulesByName) {
        // 1. Walk the path segments, skip build-tool noise, try each meaningful
        //    segment as a module name.
        String[] segments = path.split("[/\\\\]");
        for (String seg : segments) {
            if (seg.isBlank() || SKIP_SEGMENTS.contains(seg.toLowerCase(Locale.ROOT))) continue;

            // Strip extension for matching
            String cleanSeg = seg.contains(".") ? seg.substring(0, seg.lastIndexOf('.')) : seg;
            String lower = cleanSeg.toLowerCase(Locale.ROOT);

            if (modulesByName.containsKey(lower)) {
                return modulesByName.get(lower).getName();
            }

            // Partial prefix match: "paymentService" module matches "payment" segment
            for (Map.Entry<String, CodeModule> entry : modulesByName.entrySet()) {
                if (entry.getKey().startsWith(lower) || lower.startsWith(entry.getKey())) {
                    return entry.getValue().getName();
                }
            }

            // First non-trivial segment wins if no module matches – use as-is
            return toModuleName(cleanSeg);
        }
        return "Unclassified";
    }

    private String inferWithLlm(List<String> changedPaths, List<CodeModule> modules) {
        String moduleList = modules.stream().map(CodeModule::getName).collect(Collectors.joining(", "));
        String pathList = changedPaths.stream().limit(30).collect(Collectors.joining("\n"));

        String prompt = """
                Given the following changed file paths from a pull request, identify
                which existing module they most likely belong to.
                Reply with ONLY the module name, nothing else. If none match, reply: Unclassified

                Existing modules: %s

                Changed paths:
                %s
                """.formatted(moduleList, pathList);

        try {
            String response = llmService.analyzeImpact(prompt);
            String candidate = response.trim().split("\\s")[0];
            // Validate against known module names
            return modules.stream()
                    .map(CodeModule::getName)
                    .filter(n -> n.equalsIgnoreCase(candidate))
                    .findFirst()
                    .orElse(candidate.isBlank() ? "Unclassified" : candidate);
        } catch (Exception e) {
            log.warn("LLM module inference failed, falling back to Unclassified: {}", e.getMessage());
            return "Unclassified";
        }
    }

    /** Extracts the list of changed file paths from a unified diff string. */
    private List<String> extractChangedPaths(String diff) {
        if (diff == null) return List.of();
        List<String> paths = new ArrayList<>();
        // Match "diff --git a/path b/path" lines
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

    /** Converts a raw segment to a PascalCase-ish module name. */
    private String toModuleName(String segment) {
        if (segment == null || segment.isBlank()) return "Unclassified";
        return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }
}
