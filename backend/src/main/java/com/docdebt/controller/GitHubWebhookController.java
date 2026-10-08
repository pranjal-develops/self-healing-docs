package com.docdebt.controller;

import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import com.docdebt.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GitHub webhook receiver.
 *
 * Improvements:
 * - Returns 202 Accepted immediately; all processing is async (GitHub has a
 *   ~10 s timeout before it marks the delivery as failed).
 * - Deduplicates on X-GitHub-Delivery so redeliveries produce no duplicate
 *   summaries.
 * - Signature check uses raw bytes (matching GitHub's HMAC computation).
 * - Fails closed: no secret + allow-unsigned=false → 401.
 * - Handles both "opened"/"synchronize" (impact analysis) and "closed"+"merged"
 *   (summarise + auto-heal) PR actions.
 * - On first webhook for a new repo, auto-initialises the module record so
 *   docs are created from scratch without manual intervention.
 * - When docdebt.github.post-pr-comments=true, the impact analysis is posted
 *   as a comment on the PR (visible to developers) instead of just logged.
 */
@RestController
@RequestMapping("/webhook")
@Slf4j
public class GitHubWebhookController {

    private final GitHubService gitHubService;
    private final LlmService llmService;
    private final ModuleRepository moduleRepository;
    private final PrSummaryRepository prSummaryRepository;
    private final VolatilityService volatilityService;
    private final ImpactAnalysisService impactAnalysisService;
    private final HealingService healingService;
    private final ModuleResolver moduleResolver;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${docdebt.github.default-storage-target:github}")
    private String defaultStorageTarget;

    @Value("${docdebt.github.default-pr-heal-threshold:1}")
    private int defaultPrHealThreshold;

    public GitHubWebhookController(GitHubService gitHubService,
                                    LlmService llmService,
                                    ModuleRepository moduleRepository,
                                    PrSummaryRepository prSummaryRepository,
                                    VolatilityService volatilityService,
                                    ImpactAnalysisService impactAnalysisService,
                                    HealingService healingService,
                                    ModuleResolver moduleResolver) {
        this.gitHubService = gitHubService;
        this.llmService = llmService;
        this.moduleRepository = moduleRepository;
        this.prSummaryRepository = prSummaryRepository;
        this.volatilityService = volatilityService;
        this.impactAnalysisService = impactAnalysisService;
        this.healingService = healingService;
        this.moduleResolver = moduleResolver;
    }

    /**
     * Webhook endpoint. Configure in repo Settings → Webhooks with:
     *   Content type: application/json
     *   Events: Pull requests
     *
     * Returns 202 immediately; processing happens asynchronously.
     */
    @PostMapping("/github")
    public ResponseEntity<String> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId) {

        // 1. Signature validation (fail-closed)
        if (!gitHubService.isValidSignature(rawBody, signature)) {
            log.warn("Rejected webhook delivery {}: invalid or missing signature", deliveryId);
            return ResponseEntity.status(401).body("invalid signature");
        }

        // 2. Deduplicate redeliveries
        if (deliveryId != null && prSummaryRepository.existsByDeliveryId(deliveryId)) {
            log.info("Duplicate delivery {} — skipping (already processed)", deliveryId);
            return ResponseEntity.accepted().body("duplicate delivery — ignored");
        }

        // 3. Handle ping event (sent immediately when a webhook is created in GitHub repo settings)
        if ("ping".equals(eventType)) {
            log.info("Received GitHub ping event for delivery {} — auto-scaffolding initial repository docs", deliveryId);
            processPingAsync(rawBody);
            return ResponseEntity.accepted().body("ping received — auto-created module and queued initial doc scaffolding");
        }

        // 4. Handle pull_request events
        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok("ignored (not a pull_request or ping event)");
        }

        // 5. Dispatch async — return 202 immediately so GitHub doesn't time out
        processAsync(rawBody, deliveryId);
        return ResponseEntity.accepted().body("accepted for async processing");
    }

    @Async
    protected void processAsync(byte[] rawBody, String deliveryId) {
        try {
            JsonNode json = mapper.readTree(rawBody);
            String action = json.path("action").asText();
            boolean merged = json.path("pull_request").path("merged").asBoolean(false);

            long prNumber     = json.path("pull_request").path("number").asLong();
            String prTitle    = json.path("pull_request").path("title").asText();
            String prBody     = json.path("pull_request").path("body").asText(null);
            String prUrl      = json.path("pull_request").path("html_url").asText();
            String author     = json.path("pull_request").path("user").path("login").asText();
            String repoFull   = json.path("repository").path("full_name").asText();
            String baseBranch = json.path("pull_request").path("base").path("ref").asText("main");

            log.info("Processing PR event: action={}, PR=#{}, repo={}, delivery={}", action, prNumber, repoFull, deliveryId);

            // -- Impact analysis (PR opened or new commit pushed) --
            if ("opened".equals(action) || "synchronize".equals(action)) {
                String diff = safeFetchDiff(repoFull, prNumber);
                handlePROpenedOrUpdated(prNumber, prTitle, prBody, diff, repoFull, baseBranch);
                return;
            }

            // -- Summarise + heal (PR merged) --
            if (!"closed".equals(action) || !merged) {
                log.debug("Ignoring PR event: action={}, merged={}", action, merged);
                return;
            }

            String diff = safeFetchDiff(repoFull, prNumber);

            // Resolve to one or more modules (a single PR can touch multiple modules)
            Map<String, List<String>> moduleToFiles = moduleResolver.resolveModules(diff);

            for (Map.Entry<String, List<String>> entry : moduleToFiles.entrySet()) {
                String moduleName = entry.getKey();
                try {
                    processModuleForMerge(moduleName, prNumber, prTitle, prBody, prUrl,
                            author, repoFull, baseBranch, diff, deliveryId);
                } catch (Exception e) {
                    log.error("Failed to process module '{}' for PR #{}: {}", moduleName, prNumber, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Async webhook processing failed (delivery={}): {}", deliveryId, e.getMessage(), e);
        }
    }

    private void processModuleForMerge(String moduleName, long prNumber, String prTitle,
                                        String prBody, String prUrl, String author,
                                        String repoFull, String baseBranch,
                                        String diff, String deliveryId) {
        // Get or create the module record
        CodeModule module = moduleRepository.findByName(moduleName).orElseGet(() -> {
            log.info("Auto-creating module '{}' (first webhook from repo {})", moduleName, repoFull);
            CodeModule m = new CodeModule(moduleName, null, null, defaultStorageTarget, defaultPrHealThreshold);
            return moduleRepository.save(m);
        });

        // Update repo tracking info
        module.setRepositoryFullName(repoFull);
        module.setTargetBranch(baseBranch);
        moduleRepository.save(module);

        // Deduplicate at the module level using the delivery ID
        if (deliveryId != null && prSummaryRepository.existsByDeliveryId(deliveryId + ":" + moduleName)) {
            log.info("Duplicate delivery {}:{} — skipping", deliveryId, moduleName);
            return;
        }

        // Summarise the diff
        LlmService.DualSummary summary = llmService.summarizeDiff(prTitle, prBody, diff);

        // Persist the PR summary
        PrSummary prSummary = new PrSummary(module, String.valueOf(prNumber), prUrl, author,
                summary.technicalSummary(), summary.businessSummary(),
                deliveryId != null ? deliveryId + ":" + moduleName : null);
        prSummaryRepository.save(prSummary);

        // Recalculate volatility
        int score = volatilityService.recalculate(module);

        // Auto-heal if the module has hit its PR threshold
        long pendingCount = prSummaryRepository.countByModuleAndProcessedFalse(module);
        int threshold = module.getPrHealThreshold();

        if (pendingCount >= threshold) {
            log.info("Module '{}' has {} pending PR(s) ≥ threshold {} — triggering auto-heal",
                    moduleName, pendingCount, threshold);
            try {
                healingService.heal(module);
            } catch (Exception e) {
                log.error("Auto-heal failed for module '{}': {}", moduleName, e.getMessage(), e);
            }
        } else {
            log.info("Module '{}' has {} pending PR(s) < threshold {} (score={}) — queuing",
                    moduleName, pendingCount, threshold, score);
        }
    }

    private void handlePROpenedOrUpdated(long prNumber, String prTitle, String prBody,
                                          String diff, String repoFull, String baseBranch) {
        log.info("Running impact analysis for PR #{} in {}", prNumber, repoFull);

        ImpactAnalysisService.ImpactResult impact = impactAnalysisService.analyzeImpact(prTitle, prBody, diff);

        // Build the markdown comment body
        StringBuilder comment = new StringBuilder();
        comment.append("## 📋 Doc-Debt Impact Analysis\n\n");

        if (impact.affectedModules().isEmpty()) {
            comment.append("No modules appear to be affected by this PR based on current module registry.\n");
        } else {
            comment.append("**Affected modules:** ")
                    .append(String.join(", ", impact.affectedModules()))
                    .append("\n\n");
            impact.docUpdatesNeeded().forEach((module, updates) -> {
                comment.append("### `").append(module).append("`\n");
                updates.forEach(u -> comment.append("- ").append(u).append("\n"));
            });
        }
        comment.append("\n_Generated by [Doc-Debt Tracker](https://github.com/pranjal-develops/self-healing-docs)_");

        // Post as PR comment (if enabled)
        gitHubService.postPrComment(repoFull, prNumber, comment.toString());

        // Always log (so the analysis is visible even when comments are disabled)
        log.info("Impact analysis for PR #{}: affectedModules={}, docUpdates={}",
                prNumber, impact.affectedModules(), impact.docUpdatesNeeded());
    }

    private String safeFetchDiff(String repoFull, long prNumber) {
        try {
            return gitHubService.fetchPullRequestDiff(repoFull, prNumber);
        } catch (Exception e) {
            log.warn("Failed to fetch diff for PR #{} in {}: {}", prNumber, repoFull, e.getMessage());
            return "";
        }
    }

    @Async
    protected void processPingAsync(byte[] rawBody) {
        try {
            JsonNode json = mapper.readTree(rawBody);
            String repoFull = json.path("repository").path("full_name").asText();
            String repoName = json.path("repository").path("name").asText();
            String description = json.path("repository").path("description").asText(null);
            String defaultBranch = json.path("repository").path("default_branch").asText("main");

            if (repoFull == null || repoFull.isBlank()) {
                log.warn("Received ping webhook without repository.full_name — skipping");
                return;
            }

            String moduleName = (repoName != null && !repoName.isBlank()) ? repoName : repoFull;

            CodeModule module = moduleRepository.findByName(moduleName).orElseGet(() -> {
                log.info("Ping received: Auto-creating module '{}' for repo {}", moduleName, repoFull);
                CodeModule m = new CodeModule(moduleName, null, null, defaultStorageTarget, defaultPrHealThreshold);
                return moduleRepository.save(m);
            });

            module.setRepositoryFullName(repoFull);
            module.setTargetBranch(defaultBranch);
            moduleRepository.save(module);

            log.info("Ping received: Auto-scaffolding initial documentation for module '{}' (target: {})",
                    moduleName, module.getDocStorageTarget());

            healingService.scaffoldInitialDocs(module, description);

        } catch (Exception e) {
            log.error("Failed to process webhook ping event: {}", e.getMessage(), e);
        }
    }
}
