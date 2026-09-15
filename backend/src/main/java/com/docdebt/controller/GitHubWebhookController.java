package com.docdebt.controller;

import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import com.docdebt.service.GeminiService;
import com.docdebt.service.GitHubService;
import com.docdebt.service.VolatilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubService gitHubService;
    private final GeminiService geminiService;
    private final ModuleRepository moduleRepository;
    private final PrSummaryRepository prSummaryRepository;
    private final VolatilityService volatilityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubWebhookController(GitHubService gitHubService,
                                    GeminiService geminiService,
                                    ModuleRepository moduleRepository,
                                    PrSummaryRepository prSummaryRepository,
                                    VolatilityService volatilityService) {
        this.gitHubService = gitHubService;
        this.geminiService = geminiService;
        this.moduleRepository = moduleRepository;
        this.prSummaryRepository = prSummaryRepository;
        this.volatilityService = volatilityService;
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

            if (!"closed".equals(action) || !merged) {
                return ResponseEntity.ok("ignored (not a merge)");
            }

            long prNumber = json.path("pull_request").path("number").asLong();
            String prTitle = json.path("pull_request").path("title").asText();
            String prBody = json.path("pull_request").path("body").asText(null);
            String prUrl = json.path("pull_request").path("html_url").asText();
            String author = json.path("pull_request").path("user").path("login").asText();
            String repoFullName = json.path("repository").path("full_name").asText();

            // 1. Map phase: fetch diff, get both a technical and a business summary
            String diff = gitHubService.fetchPullRequestDiff(repoFullName, prNumber);
            String moduleName = gitHubService.inferModuleFromDiff(diff);
            GeminiService.DualSummary summary = geminiService.summarizeDiff(prTitle, prBody, diff);

            // 2. Persist against the module (create module record if new)
            CodeModule module = moduleRepository.findByName(moduleName)
                    .orElseGet(() -> moduleRepository.save(new CodeModule(moduleName, null, null)));

            PrSummary prSummary = new PrSummary(module, String.valueOf(prNumber), prUrl, author,
                    summary.technicalSummary(), summary.businessSummary());
            prSummaryRepository.save(prSummary);

            // 3. Recalculate volatility (does NOT touch the docs - that's the nightly/manual job)
            volatilityService.recalculate(module);

            return ResponseEntity.ok("processed PR #%d for module %s".formatted(prNumber, moduleName));
        } catch (Exception e) {
            log.error("Failed to process GitHub webhook", e);
            return ResponseEntity.status(500).body("error: " + e.getMessage());
        }
    }
}
