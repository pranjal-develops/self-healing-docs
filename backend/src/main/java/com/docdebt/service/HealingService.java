package com.docdebt.service;

import com.docdebt.dto.HealResultDto;
import com.docdebt.dto.HealResultDto.DocHealResult;
import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Implements the "Reduce" phase + Enterprise Bridge from the project plan -
 * run once per doc type (technical, business): gather unprocessed summaries
 * -> semantic discovery (match existing doc or scaffold new one) -> synthesize
 * with Gemini -> publish as an unpublished draft -> clear pending summaries &
 * reset volatility once both docs are done.
 */
@Service
@Slf4j
public class HealingService {

    private final PrSummaryRepository prSummaryRepository;
    private final ModuleRepository moduleRepository;
    private final DocStorageService docStorageService;
    private final LlmService llmService;
    private final SemanticDiscoveryService semanticDiscoveryService;
    private final GitHubService gitHubService;

    public HealingService(PrSummaryRepository prSummaryRepository,
                           ModuleRepository moduleRepository,
                           DocStorageService docStorageService,
                           LlmService llmService,
                           SemanticDiscoveryService semanticDiscoveryService,
                           GitHubService gitHubService) {
        this.prSummaryRepository = prSummaryRepository;
        this.moduleRepository = moduleRepository;
        this.docStorageService = docStorageService;
        this.llmService = llmService;
        this.semanticDiscoveryService = semanticDiscoveryService;
        this.gitHubService = gitHubService;
    }

    public HealResultDto heal(CodeModule module) {
        List<PrSummary> unprocessed = prSummaryRepository.findByModuleAndProcessedFalse(module);

        if (unprocessed.isEmpty()) {
            throw new IllegalStateException(
                    "Module '%s' has no pending PR summaries - nothing to heal from. ".formatted(module.getName()) +
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
        DocHealResult businessResult = healOne(module, DocType.BUSINESS, aggregatedBusiness);

        // Clear the queue and reset the score once both docs are healed.
        unprocessed.forEach(s -> s.setProcessed(true));
        prSummaryRepository.saveAll(unprocessed);
        module.setLastDocUpdate(LocalDateTime.now());
        module.setVolatilityScore(0);
        moduleRepository.save(module);

        return new HealResultDto(module.getName(), technicalResult, businessResult, unprocessed.size());
    }

    private DocHealResult healOne(CodeModule module, DocType type, String aggregated) {
        String path = type == DocType.TECHNICAL ? module.getTechnicalDocPath() : module.getBusinessDocPath();
        String repoFullName = module.getRepositoryFullName();
        String targetBranch = module.getTargetBranch();

        boolean wasScaffolded;
        String oldContent = null;
        String newContent;

        // Try reading existing file from GitHub repository first if repo is known
        if (repoFullName != null && !repoFullName.isBlank() && path != null && !path.isBlank()) {
            GitHubService.GitHubFile ghFile = gitHubService.getFileContent(repoFullName, path, targetBranch);
            if (ghFile != null) {
                oldContent = ghFile.content();
            }
        }

        // Fallback to local storage service if content wasn't loaded from GitHub
        if (oldContent == null && path != null && !path.isBlank()) {
            oldContent = docStorageService.getDocumentContent(type, path);
        }

        if (oldContent != null) {
            wasScaffolded = false;
            newContent = synthesize(type, oldContent, aggregated, module.getName());
        } else {
            // No mapping/content yet - run semantic discovery against other modules' docs
            SemanticDiscoveryService.MatchResult match = semanticDiscoveryService.findBestMatch(type, aggregated);
            if (match.match().isPresent()) {
                CodeModule matched = match.match().get();
                String matchedPath = type == DocType.TECHNICAL ? matched.getTechnicalDocPath() : matched.getBusinessDocPath();
                path = matchedPath;

                if (repoFullName != null && !repoFullName.isBlank()) {
                    GitHubService.GitHubFile ghFile = gitHubService.getFileContent(repoFullName, matchedPath, targetBranch);
                    oldContent = ghFile != null ? ghFile.content() : null;
                }
                if (oldContent == null) {
                    oldContent = docStorageService.getDocumentContent(type, matchedPath);
                }

                newContent = oldContent != null
                        ? synthesize(type, oldContent, aggregated, module.getName())
                        : scaffold(type, module.getName(), aggregated);
                wasScaffolded = (oldContent == null);
            } else {
                // Auto-Scaffolding pipeline: brand new doc from template.
                oldContent = "";
                newContent = scaffold(type, module.getName(), aggregated);
                String cleanModuleName = module.getName().replaceAll("\\.[^/.]+$", "");
                path = "docs/" + (type == DocType.TECHNICAL ? "Technical/" : "Business/") + cleanModuleName + (type == DocType.TECHNICAL ? "-HLD.md" : "-Business.md");
                wasScaffolded = true;
            }
            applyPath(module, type, path, wasScaffolded);
        }

        String cleanModuleName = module.getName().replaceAll("\\.[^/.]+$", "");
        String fileName = path != null ? path : "docs/" + (type == DocType.TECHNICAL ? "Technical/" : "Business/") + cleanModuleName + (type == DocType.TECHNICAL ? "-HLD.md" : "-Business.md");
        String draftPath = docStorageService.pushDraft(type, fileName, newContent);

        // Push directly to target GitHub Repository if configured
        if (repoFullName != null && !repoFullName.isBlank()) {
            try {
                String commitMessage = "[Doc-Debt Tracker] Auto-healed %s documentation for %s"
                        .formatted(type.name().toLowerCase(), module.getName());
                String githubUrl = gitHubService.createOrUpdateFile(repoFullName, fileName, newContent, commitMessage, targetBranch);
                draftPath = githubUrl;
                log.info("Successfully pushed updated doc to GitHub: {}", githubUrl);
            } catch (Exception e) {
                log.error("Failed to commit doc update to GitHub repo {}: {}", repoFullName, e.getMessage(), e);
            }
        }

        // Index this module's embedding so future modules can be semantically matched to it.
        semanticDiscoveryService.storeEmbedding(type, module, newContent);

        return new DocHealResult(oldContent != null ? oldContent : "", newContent, draftPath, wasScaffolded);
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
}
