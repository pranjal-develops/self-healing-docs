package com.docdebt.controller;

import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import com.docdebt.service.LlmService;
import com.docdebt.service.LlmService.DualSummary;
import com.docdebt.service.GitHubService;
import com.docdebt.service.ImpactAnalysisService;
import com.docdebt.service.VolatilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.docdebt.service.HealingService;

@RestController
@RequestMapping("/webhook")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubService gitHubService;
    private final LlmService llmService;
    private final ModuleRepository moduleRepository;
    private final PrSummaryRepository prSummaryRepository;
    private final VolatilityService volatilityService;
    private final ImpactAnalysisService impactAnalysisService;
    private final HealingService healingService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubWebhookController(GitHubService gitHubService,
                                    LlmService llmService,
                                    ModuleRepository moduleRepository,
                                    PrSummaryRepository prSummaryRepository,
                                    VolatilityService volatilityService,
                                    ImpactAnalysisService impactAnalysisService,
                                    HealingService healingService) {
        this.gitHubService = gitHubService;
        this.llmService = llmService;
        this.moduleRepository = moduleRepository;
        this.prSummaryRepository = prSummaryRepository;
        this.volatilityService = volatilityService;
        this.impactAnalysisService = impactAnalysisService;
        this.healingService = healingService;
    }

    /**
     * GitHub webhook receiver. Configure this URL under repo Settings > Webhooks,
     * content type application/json, event type "Pull requests".
     */
    @PostMapping("/github")
    public ResponseEntity<String> handlePullRequestEvent(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType) {

        if (!gitHubService.isValidSignature(rawBody, signature)) {
            log.warn("Rejected webhook delivery: invalid signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok("ignored (not a pull_request event)");
        }

        try {
            var json = mapper.readTree(rawBody);
            String action = json.path("action").asText();
            boolean merged = json.path("pull_request").path("merged").asBoolean(false);

            long prNumber = json.path("pull_request").path("number").asLong();
            String prTitle = json.path("pull_request").path("title").asText();
            String prBody = json.path("pull_request").path("body").asText(null);
            String prUrl = json.path("pull_request").path("html_url").asText();
            String author = json.path("pull_request").path("user").path("login").asText();
            String repoFullName = json.path("repository").path("full_name").asText();
            String baseBranch = json.path("pull_request").path("base").path("ref").asText("main");

            String diff = gitHubService.fetchPullRequestDiff(repoFullName, prNumber);

            if ("opened".equals(action)) {
                handlePROpened(prNumber, prTitle, prBody, diff, repoFullName);
                return ResponseEntity.ok("impact analysis triggered for PR #%d".formatted(prNumber));
            }

            if (!"closed".equals(action) || !merged) {
                return ResponseEntity.ok("ignored (not a merge)");
            }

            // 1. Map phase: fetch diff, get both a technical and a business summary
            String moduleName = gitHubService.inferModuleFromDiff(diff);
            DualSummary summary = llmService.summarizeDiff(prTitle, prBody, diff);

            // 2. Persist against the module (create module record if new)
            CodeModule module = moduleRepository.findByName(moduleName)
                    .orElseGet(() -> moduleRepository.save(new CodeModule(moduleName, null, null)));

            module.setRepositoryFullName(repoFullName);
            module.setTargetBranch(baseBranch);
            moduleRepository.save(module);

            PrSummary prSummary = new PrSummary(module, String.valueOf(prNumber), prUrl, author,
                    summary.technicalSummary(), summary.businessSummary());
            prSummaryRepository.save(prSummary);

            // 3. Recalculate volatility
            volatilityService.recalculate(module);

            // 4. Automatically trigger doc healing & commit directly back to GitHub repository
            try {
                log.info("Triggering automatic document healing and direct GitHub commit for module: {}", moduleName);
                healingService.heal(module);
            } catch (Exception e) {
                log.error("Automatic doc healing failed for module {}: {}", moduleName, e.getMessage(), e);
            }

            return ResponseEntity.ok("processed PR #%d and updated docs for module %s".formatted(prNumber, moduleName));
        } catch (Exception e) {
            log.error("Failed to process GitHub webhook", e);
            return ResponseEntity.status(500).body("error: " + e.getMessage());
        }
    }

    private void handlePROpened(long prNumber, String prTitle, String prBody, String diff, String repoFullName) {
        log.info("PR opened - triggering impact analysis: PR #{} - {}", prNumber, prTitle);

        ImpactAnalysisService.ImpactResult impact = impactAnalysisService.analyzeImpact(prTitle, prBody, diff);

        log.info("Impact analysis complete for PR #{}: affectedModules={}, docUpdates={}",
                prNumber, impact.affectedModules(), impact.docUpdatesNeeded());

        impact.docUpdatesNeeded().forEach((module, updates) -> {
            log.info("Module '{}' needs updates: {}", module, String.join(", ", updates));
        });
    }
}
