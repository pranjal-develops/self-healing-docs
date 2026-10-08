package com.docdebt.controller;

import com.docdebt.dto.HealResultDto;
import com.docdebt.dto.ModuleStatusDto;
import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import com.docdebt.service.HealingService;
import com.docdebt.service.VolatilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;

/**
 * Dashboard REST API.
 *
 * Security: all endpoints require an X-API-Key header that matches
 * docdebt.api.key. If docdebt.api.allow-anonymous=true the check is
 * skipped (use only for local development).
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class DashboardController {

    private final ModuleRepository moduleRepository;
    private final PrSummaryRepository prSummaryRepository;
    private final VolatilityService volatilityService;
    private final HealingService healingService;

    @Value("${docdebt.api.key:}")
    private String apiKey;

    @Value("${docdebt.api.allow-anonymous:true}")
    private boolean allowAnonymous;

    @Value("${docdebt.demo.enabled:true}")
    private boolean demoEnabled;

    // ---- Fake data for the Time Travel simulator ----
    private static final String[] FAKE_PR_TITLES = {
            "Refactor request validation",
            "Add retry logic to downstream call",
            "Introduce new response field",
            "Bump dependency & fix breaking change",
            "Add circuit breaker around external API"
    };
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

    // ---- Auth helper -------------------------------------------------------

    /**
     * Returns a 403 ResponseEntity if the API key check fails, null otherwise.
     * Callers should: var authError = checkAuth(key); if (authError != null) return authError;
     */
    private ResponseEntity<?> checkAuth(String providedKey) {
        if (allowAnonymous) return null;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No API key configured and allow-anonymous=false. Set DOCDEBT_API_KEY or docdebt.api.allow-anonymous=true.");
            return ResponseEntity.status(403).body(new ErrorResponse("API key not configured on server."));
        }
        if (!apiKey.equals(providedKey)) {
            return ResponseEntity.status(403).body(new ErrorResponse("Invalid or missing X-API-Key header."));
        }
        return null;
    }

    // ---- Endpoints ---------------------------------------------------------

    /** Powers the Debt Heatmap. */
    @GetMapping("/modules")
    public ResponseEntity<?> listModules(
            @RequestHeader(value = "X-API-Key", required = false) String key) {
        var auth = checkAuth(key);
        if (auth != null) return auth;

        int threshold = volatilityService.getThreshold();
        List<ModuleStatusDto> result = moduleRepository.findAll().stream()
                .map(m -> ModuleStatusDto.from(m, prSummaryRepository.countByModuleAndProcessedFalse(m), threshold))
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Creates a module manually. Useful for seeding a demo without a real repo
     * or for pre-registering a module before the first PR lands.
     *
     * Body: { name, technicalDocPath?, businessDocPath?,
     *         docStorageTarget?, prHealThreshold? }
     */
    @PostMapping("/modules")
    public ResponseEntity<?> createModule(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @RequestBody CreateModuleRequest request) {
        var auth = checkAuth(key);
        if (auth != null) return auth;

        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Module name is required."));
        }

        // Check duplicate
        if (moduleRepository.findByName(name).isPresent()) {
            return ResponseEntity.status(409).body(new ErrorResponse("Module '" + name + "' already exists."));
        }

        String target = request.docStorageTarget() != null ? request.docStorageTarget() : "github";
        int threshold = request.prHealThreshold() != null && request.prHealThreshold() > 0
                ? request.prHealThreshold() : 1;

        CodeModule module = new CodeModule(name, request.technicalDocPath(), request.businessDocPath(), target, threshold);
        return ResponseEntity.ok(moduleRepository.save(module));
    }

    public record UpdateModuleSettingsRequest(String docStorageTarget, Integer prHealThreshold) {}

    /** Updates storage target and/or PR heal threshold for an existing module. */
    @PatchMapping("/modules/{id}")
    public ResponseEntity<?> updateModuleSettings(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @PathVariable Long id,
            @RequestBody UpdateModuleSettingsRequest request) {
        var auth = checkAuth(key);
        if (auth != null) return auth;

        CodeModule module = moduleRepository.findById(id).orElse(null);
        if (module == null) return ResponseEntity.notFound().build();

        if (request.docStorageTarget() != null && !request.docStorageTarget().isBlank()) {
            module.setDocStorageTarget(request.docStorageTarget().toLowerCase());
        }
        if (request.prHealThreshold() != null && request.prHealThreshold() > 0) {
            module.setPrHealThreshold(request.prHealThreshold());
        }

        moduleRepository.save(module);

        int threshold = volatilityService.getThreshold();
        return ResponseEntity.ok(ModuleStatusDto.from(
                module, prSummaryRepository.countByModuleAndProcessedFalse(module), threshold));
    }

    /** Heal Now button: manually fires the Map-Reduce healing pipeline. */
    @PostMapping("/modules/{id}/heal")
    public ResponseEntity<?> healNow(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @PathVariable Long id) {
        var auth = checkAuth(key);
        if (auth != null) return auth;

        CodeModule module = moduleRepository.findById(id).orElse(null);
        if (module == null) return ResponseEntity.notFound().build();

        try {
            HealResultDto result = healingService.heal(module);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Time Travel button: simulates N rapid PR merges.
     * Only available when docdebt.demo.enabled=true.
     */
    @PostMapping("/modules/{id}/simulate")
    public ResponseEntity<?> simulate(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") int count) {
        var auth = checkAuth(key);
        if (auth != null) return auth;

        if (!demoEnabled) {
            return ResponseEntity.status(403).body(
                    new ErrorResponse("Time Travel simulator is disabled. Set docdebt.demo.enabled=true to enable."));
        }

        CodeModule module = moduleRepository.findById(id).orElse(null);
        if (module == null) return ResponseEntity.notFound().build();

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
        volatilityService.recalculate(module);
        int threshold = volatilityService.getThreshold();
        return ResponseEntity.ok(ModuleStatusDto.from(
                module, prSummaryRepository.countByModuleAndProcessedFalse(module), threshold));
    }

    // ---- Records -----------------------------------------------------------

    public record ErrorResponse(String message) {}

    public record CreateModuleRequest(
            String name,
            String technicalDocPath,
            String businessDocPath,
            /** Where healed docs are published: "github" (default), "onedrive", "sharepoint" */
            String docStorageTarget,
            /** Auto-heal after this many unprocessed PRs. Default is 1. */
            Integer prHealThreshold
    ) {}
}
