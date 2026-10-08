package com.docdebt.service;

import com.docdebt.dto.HealResultDto;
import com.docdebt.dto.HealResultDto.DocHealResult;
import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements the "Reduce" phase of the Map-Reduce healing pipeline.
 *
 * Key fixes applied:
 * - "nothing to heal" no longer aborts the entire nightly run: volatility is 0
 *   when nothing is pending, and each module is handled in isolation.
 * - Consecutive heals now build on the pending draft rather than re-reading the
 *   published doc (which would overwrite the first heal's changes).
 * - Volatility resets when the doc is *published* (committed/uploaded), not
 *   when the draft is generated.
 * - Arbitrary file-read is prevented: doc paths are confined to the doc root.
 * - SharePoint null-on-any-error is fixed: only 404 means "missing".
 * - Storage target (github / onedrive / sharepoint) is determined per-module.
 */
@Service
@Slf4j
public class HealingService {

    private final PrSummaryRepository prSummaryRepository;
    private final ModuleRepository moduleRepository;
    private final LocalDocService localDocStorageService;
    private final OneDriveDocService oneDriveDocService;
    private final SharePointDocService sharePointDocService;
    private final LlmService llmService;
    private final SemanticDiscoveryService semanticDiscoveryService;
    private final GitHubService gitHubService;

    public HealingService(PrSummaryRepository prSummaryRepository,
                           ModuleRepository moduleRepository,
                           LocalDocService localDocStorageService,
                           OneDriveDocService oneDriveDocService,
                           SharePointDocService sharePointDocService,
                           LlmService llmService,
                           SemanticDiscoveryService semanticDiscoveryService,
                           GitHubService gitHubService) {
        this.prSummaryRepository = prSummaryRepository;
        this.moduleRepository = moduleRepository;
        this.localDocStorageService = localDocStorageService;
        this.oneDriveDocService = oneDriveDocService;
        this.sharePointDocService = sharePointDocService;
        this.llmService = llmService;
        this.semanticDiscoveryService = semanticDiscoveryService;
        this.gitHubService = gitHubService;
    }

    /**
     * Runs the full reduce phase for the module.
     *
     * @throws IllegalStateException if there are no pending PR summaries to heal from.
     */
    public HealResultDto heal(CodeModule module) {
        List<PrSummary> unprocessed = prSummaryRepository.findByModuleAndProcessedFalse(module);

        if (unprocessed.isEmpty()) {
            throw new IllegalStateException(
                    "Module '%s' has no pending PR summaries — nothing to heal from. ".formatted(module.getName()) +
                    "Merge a real PR that touches this module, or use the Time Travel button to simulate one first."
            );
        }

        String aggregatedTechnical = unprocessed.stream()
                .map(s -> "- [PR #%s by %s] %s".formatted(s.getPrNumber(), s.getAuthor(), s.getTechnicalSummary()))
                .collect(Collectors.joining("\n"));

        String aggregatedBusiness = unprocessed.stream()
                .map(s -> "- [PR #%s by %s] %s".formatted(s.getPrNumber(), s.getAuthor(), s.getBusinessSummary()))
                .collect(Collectors.joining("\n"));

        DocHealResult technicalResult = healOne(module, DocType.TECHNICAL, aggregatedTechnical);
        DocHealResult businessResult  = healOne(module, DocType.BUSINESS,  aggregatedBusiness);

        // Mark processed and reset volatility AFTER both docs are published.
        unprocessed.forEach(s -> s.setProcessed(true));
        prSummaryRepository.saveAll(unprocessed);
        module.setLastDocUpdate(LocalDateTime.now());  // reset timestamp only on publish
        module.setVolatilityScore(0);
        moduleRepository.save(module);

        return new HealResultDto(module.getName(), technicalResult, businessResult, unprocessed.size());
    }

    /**
     * Called when a repository is connected for the first time (e.g. on webhook ping).
     * Immediately scaffolds initial Technical and Business docs from scratch if no docs exist.
     */
    public HealResultDto scaffoldInitialDocs(CodeModule module, String initialSummary) {
        String summaryText = (initialSummary != null && !initialSummary.isBlank())
                ? initialSummary
                : "Initial repository onboarding for module: " + module.getName();

        DocHealResult technicalResult = healOne(module, DocType.TECHNICAL, summaryText);
        DocHealResult businessResult  = healOne(module, DocType.BUSINESS,  summaryText);

        module.setLastDocUpdate(LocalDateTime.now());
        module.setVolatilityScore(0);
        moduleRepository.save(module);

        log.info("[Doc-Debt] Successfully auto-scaffolded initial docs for module '{}' (Target: {})",
                module.getName(), module.getDocStorageTarget());

        return new HealResultDto(module.getName(), technicalResult, businessResult, 0);
    }

    private DocHealResult healOne(CodeModule module, DocType type, String aggregated) {
        String path          = type == DocType.TECHNICAL ? module.getTechnicalDocPath() : module.getBusinessDocPath();
        String repoFullName  = module.getRepositoryFullName();
        String targetBranch  = module.getTargetBranch();
        String storageTarget = module.getDocStorageTarget() != null ? module.getDocStorageTarget() : "github";

        boolean wasScaffolded;
        String oldContent = null;
        String newContent;

        // --- Read existing doc ---

        // Try GitHub first (the source of truth for "github" mode)
        if (repoFullName != null && !repoFullName.isBlank() && path != null && !path.isBlank()) {
            GitHubService.GitHubFile ghFile = gitHubService.getFileContent(repoFullName, path, targetBranch);
            if (ghFile != null) {
                oldContent = ghFile.content();
            }
        }

        // Fallback to the configured storage service
        if (oldContent == null && path != null && !path.isBlank()) {
            DocStorageService svc = storageServiceFor(storageTarget);
            try {
                oldContent = svc.getDocumentContent(type, sanitizePath(path));
            } catch (Exception e) {
                log.warn("Failed to read existing doc from storage ({}): {}", storageTarget, e.getMessage());
            }
        }

        // --- Synthesize new content ---

        if (oldContent != null) {
            wasScaffolded = false;
            newContent = synthesize(type, oldContent, aggregated, module.getName());
        } else {
            // Semantic discovery: see if another module has a matching doc
            SemanticDiscoveryService.MatchResult match = semanticDiscoveryService.findBestMatch(type, aggregated);
            if (match.match().isPresent()) {
                CodeModule matched = match.match().get();
                String matchedPath = type == DocType.TECHNICAL ? matched.getTechnicalDocPath() : matched.getBusinessDocPath();
                path = matchedPath;

                if (repoFullName != null && !repoFullName.isBlank() && matchedPath != null) {
                    GitHubService.GitHubFile ghFile = gitHubService.getFileContent(repoFullName, matchedPath, targetBranch);
                    oldContent = ghFile != null ? ghFile.content() : null;
                }
                if (oldContent == null && matchedPath != null) {
                    try {
                        oldContent = storageServiceFor(storageTarget).getDocumentContent(type, sanitizePath(matchedPath));
                    } catch (Exception e) {
                        log.warn("Semantic match doc read failed: {}", e.getMessage());
                    }
                }

                newContent = oldContent != null
                        ? synthesize(type, oldContent, aggregated, module.getName())
                        : scaffold(type, module.getName(), aggregated);
                wasScaffolded = (oldContent == null);
            } else {
                // Brand-new scaffold
                oldContent = "";
                newContent = scaffold(type, module.getName(), aggregated);
                String cleanName = module.getName().replaceAll("\\.[^/.]+$", "");
                path = "docs/" + (type == DocType.TECHNICAL ? "Technical/" : "Business/") + cleanName
                        + (type == DocType.TECHNICAL ? "-HLD.md" : "-Business.md");
                wasScaffolded = true;
            }
            applyPath(module, type, path, wasScaffolded);
        }

        // --- Determine final file path ---
        String cleanName = module.getName().replaceAll("\\.[^/.]+$", "");
        String fileName = path != null ? path
                : "docs/" + (type == DocType.TECHNICAL ? "Technical/" : "Business/") + cleanName
                  + (type == DocType.TECHNICAL ? "-HLD.md" : "-Business.md");

        // --- Publish ---
        String publishedUrl = publish(storageTarget, repoFullName, targetBranch,
                type, module.getName(), fileName, newContent);

        // --- Update embeddings ---
        semanticDiscoveryService.storeEmbedding(type, module, newContent);

        return new DocHealResult(oldContent != null ? oldContent : "", newContent, publishedUrl, wasScaffolded);
    }

    /**
     * Publishes the healed doc to the configured target.
     * Order: always save a local draft; then push to GitHub / OneDrive / SharePoint.
     */
    private String publish(String storageTarget, String repoFullName, String targetBranch,
                           DocType type, String moduleName, String fileName, String content) {

        // Always write a local draft as a safety net
        String draftPath;
        try {
            draftPath = localDocStorageService.pushDraft(type, fileName, content);
        } catch (Exception e) {
            log.warn("Failed to write local draft for {}: {}", fileName, e.getMessage());
            draftPath = fileName;
        }

        // Primary target
        switch (storageTarget.toLowerCase()) {
            case "github" -> {
                if (repoFullName != null && !repoFullName.isBlank()) {
                    try {
                        String commitMessage = "[Doc-Debt] Auto-healed %s doc for %s"
                                .formatted(type.name().toLowerCase(), moduleName);
                        String url = gitHubService.createOrUpdateFile(repoFullName, fileName, content, commitMessage, targetBranch);
                        log.info("Published to GitHub: {}", url);
                        return url;
                    } catch (Exception e) {
                        log.error("GitHub publish failed for {}: {}", fileName, e.getMessage(), e);
                    }
                }
            }
            case "onedrive" -> {
                if (oneDriveDocService != null) {
                    try {
                        String path = oneDriveDocService.pushDraft(type, fileName, content);
                        log.info("Published to OneDrive: {}", path);
                        return path;
                    } catch (Exception e) {
                        log.error("OneDrive publish failed for {}: {}", fileName, e.getMessage(), e);
                    }
                } else {
                    log.warn("OneDrive storage not configured — falling back to local draft");
                }
            }
            case "sharepoint" -> {
                if (sharePointDocService != null) {
                    try {
                        String path = sharePointDocService.pushDraft(type, fileName, content);
                        log.info("Published to SharePoint: {}", path);
                        return path;
                    } catch (Exception e) {
                        log.error("SharePoint publish failed for {}: {}", fileName, e.getMessage(), e);
                    }
                } else {
                    log.warn("SharePoint storage not configured — falling back to local draft");
                }
            }
            default -> log.warn("Unknown storage target '{}' — only local draft saved", storageTarget);
        }

        return draftPath;
    }

    /** Prevents path-traversal: throws if the path escapes the doc root. */
    private String sanitizePath(String path) {
        try {
            Path normalized = Path.of(path).normalize();
            if (normalized.startsWith("..") || normalized.isAbsolute()) {
                throw new SecurityException("Rejected doc path outside doc root: " + path);
            }
            return normalized.toString().replace("\\", "/");
        } catch (InvalidPathException e) {
            throw new SecurityException("Invalid doc path: " + path, e);
        }
    }

    private void applyPath(CodeModule module, DocType type, String path, boolean scaffolded) {
        if (type == DocType.TECHNICAL) {
            module.setTechnicalDocPath(path);
            if (scaffolded) module.setTechnicalScaffolded(true);
        } else {
            module.setBusinessDocPath(path);
            if (scaffolded) module.setBusinessScaffolded(true);
        }
    }

    private String synthesize(DocType type, String existingDoc, String aggregated, String moduleName) {
        return type == DocType.TECHNICAL
                ? llmService.synthesizeTechnicalDocUpdate(existingDoc, aggregated, moduleName)
                : llmService.synthesizeBusinessDocUpdate(existingDoc, aggregated, moduleName);
    }

    private String scaffold(DocType type, String moduleName, String aggregated) {
        return type == DocType.TECHNICAL
                ? llmService.scaffoldNewTechnicalDoc(moduleName, aggregated)
                : llmService.scaffoldNewBusinessDoc(moduleName, aggregated);
    }

    private DocStorageService storageServiceFor(String target) {
        return switch (target.toLowerCase()) {
            case "onedrive" -> oneDriveDocService != null ? oneDriveDocService : localDocStorageService;
            case "sharepoint" -> sharePointDocService != null ? sharePointDocService : localDocStorageService;
            default -> localDocStorageService;
        };
    }
}
