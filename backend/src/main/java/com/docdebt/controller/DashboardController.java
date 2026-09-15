package com.docdebt.controller;

import com.docdebt.dto.HealResultDto;
import com.docdebt.dto.ModuleStatusDto;
import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import com.docdebt.service.HealingService;
import com.docdebt.service.VolatilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final ModuleRepository moduleRepository;
    private final PrSummaryRepository prSummaryRepository;
    private final VolatilityService volatilityService;
    private final HealingService healingService;

    private static final String[] FAKE_PR_TITLES = {
            "Refactor request validation",
            "Add retry logic to downstream call",
            "Introduce new response field",
            "Bump dependency & fix breaking change",
            "Add circuit breaker around external API"
    };
    // Paired technical / business summaries so the simulated data exercises both docs realistically.
    // Deliberately domain-neutral (no "payment", "checkout", etc.) since this same canned data
    // gets used regardless of what module name you type - it's illustrative, not analysis of
    // your actual code.
    private static final String[] FAKE_TECHNICAL_SUMMARIES = {
            "Added input validation on the create endpoint and extracted a shared validator class.",
            "Introduced exponential backoff retries for a downstream service call.",
            "Added a new 'status' field to the API response and updated the DTO mapping.",
            "Upgraded the HTTP client dependency and adjusted timeout configuration accordingly.",
            "Wrapped an external service call in a circuit breaker to fail fast under load."
    };
    private static final String[] FAKE_BUSINESS_SUMMARIES = {
            "No user-facing business impact.",
            "Improves reliability during downstream slowdowns - fewer failed requests for users.",
            "Users can now see a live status (e.g. 'Processing', 'Complete') instead of a generic pending state.",
            "No user-facing business impact.",
            "Prevents the app from freezing when a dependency is slow - users see a faster, more consistent experience."
    };

    public DashboardController(ModuleRepository moduleRepository,
                                PrSummaryRepository prSummaryRepository,
                                VolatilityService volatilityService,
                                HealingService healingService) {
        this.moduleRepository = moduleRepository;
        this.prSummaryRepository = prSummaryRepository;
        this.volatilityService = volatilityService;
        this.healingService = healingService;
    }

    /** Powers the Debt Heatmap. */
    @GetMapping("/modules")
    public List<ModuleStatusDto> listModules() {
        int threshold = volatilityService.getThreshold();
        return moduleRepository.findAll().stream()
                .map(m -> ModuleStatusDto.from(
                        m,
                        prSummaryRepository.countByModuleAndProcessedFalse(m),
                        threshold))
                .toList();
    }

    /** Creates a module manually (useful for seeding a demo without a real repo). */
    @PostMapping("/modules")
    public CodeModule createModule(@RequestBody CreateModuleRequest request) {
        CodeModule module = new CodeModule(request.name(), request.technicalDocPath(), request.businessDocPath());
        return moduleRepository.save(module);
    }

    /** The "Heal Now" button: manually fires the Map-Reduce healing pipeline immediately (both docs). */
    @PostMapping("/modules/{id}/heal")
    public ResponseEntity<?> healNow(@PathVariable Long id) {
        CodeModule module = moduleRepository.findById(id).orElseThrow();
        try {
            HealResultDto result = healingService.heal(module);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            // Thrown when there's no pending PR history to heal from - refuse rather
            // than let the LLM invent content with nothing real to base it on.
            return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage()));
        }
    }

    public record ErrorResponse(String message) {}

    /**
     * The "Time Travel" button: simulates N rapid PR merges against a module
     * without needing real GitHub traffic, so judges can watch the score spike.
     */
    @PostMapping("/modules/{id}/simulate")
    public ResponseEntity<ModuleStatusDto> simulate(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "5") int count) {
        CodeModule module = moduleRepository.findById(id).orElseThrow();
        Random rand = new Random();
        for (int i = 0; i < count; i++) {
            int idx = rand.nextInt(FAKE_TECHNICAL_SUMMARIES.length);
            PrSummary summary = new PrSummary(
                    module,
                    String.valueOf(1000 + rand.nextInt(9000)),
                    "https://github.com/example/repo/pull/" + (1000 + i),
                    "demo-user",
                    FAKE_TECHNICAL_SUMMARIES[idx] + " (" + FAKE_PR_TITLES[idx] + ")",
                    FAKE_BUSINESS_SUMMARIES[idx]
            );
            prSummaryRepository.save(summary);
        }
        int score = volatilityService.recalculate(module);
        return ResponseEntity.ok(ModuleStatusDto.from(
                module,
                prSummaryRepository.countByModuleAndProcessedFalse(module),
                volatilityService.getThreshold()));
    }

    public record CreateModuleRequest(String name, String technicalDocPath, String businessDocPath) {}
}
