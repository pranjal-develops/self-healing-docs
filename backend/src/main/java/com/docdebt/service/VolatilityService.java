package com.docdebt.service;

import com.docdebt.entity.CodeModule;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Computes and persists the volatility score for every module.
 *
 * Score = (unprocessed PR summaries × prWeight) + (days since last doc update × dayWeight)
 *
 * Key fixes:
 * - evaluateAll() handles each module in isolation — one module throwing
 *   "nothing to heal" no longer aborts the rest of the batch.
 * - volatilityScore is 0 when there are no pending summaries (not negative).
 * - The nightly batch only triggers healing when the module actually has
 *   pending summaries (avoids the "nothing to heal" exception entirely).
 */
@Slf4j
@Service
public class VolatilityService {

    private final ModuleRepository moduleRepository;
    private final PrSummaryRepository prSummaryRepository;
    private final HealingService healingService;

    @Value("${docdebt.volatility.threshold:50}")
    private int threshold;

    @Value("${docdebt.volatility.pr-weight:10}")
    private int prWeight;

    @Value("${docdebt.volatility.day-weight:1}")
    private int dayWeight;

    public VolatilityService(ModuleRepository moduleRepository,
                              PrSummaryRepository prSummaryRepository,
                              HealingService healingService) {
        this.moduleRepository = moduleRepository;
        this.prSummaryRepository = prSummaryRepository;
        this.healingService = healingService;
    }

    /**
     * Recomputes the volatility score for a single module and persists it.
     * Score is always ≥ 0.
     */
    public int recalculate(CodeModule module) {
        long unprocessed = prSummaryRepository.countByModuleAndProcessedFalse(module);
        long days = ChronoUnit.DAYS.between(module.getLastDocUpdate(), LocalDateTime.now());
        int score = (int) Math.max(0, unprocessed * prWeight + days * dayWeight);
        module.setVolatilityScore(score);
        moduleRepository.save(module);
        return score;
    }

    /**
     * Nightly batch: recompute every module's score and heal anything over
     * the threshold — but only if the module actually has pending summaries.
     * Each module is handled independently so one failure doesn't block others.
     */
    public void evaluateAll() {
        List<CodeModule> modules = moduleRepository.findAll();
        log.info("Running nightly Doc-Debt evaluation for {} module(s)", modules.size());

        for (CodeModule module : modules) {
            try {
                int score = recalculate(module);
                long pending = prSummaryRepository.countByModuleAndProcessedFalse(module);

                if (score >= threshold && pending > 0) {
                    log.info("Module '{}' score={} ≥ threshold={} with {} pending PRs — healing",
                            module.getName(), score, threshold, pending);
                    healingService.heal(module);
                } else if (score >= threshold) {
                    log.info("Module '{}' score={} ≥ threshold but no pending PRs — skipping heal",
                            module.getName(), score);
                } else {
                    log.debug("Module '{}' score={} below threshold={} — OK", module.getName(), score, threshold);
                }
            } catch (Exception e) {
                // Isolate: log and continue with next module
                log.error("Failed to evaluate/heal module '{}': {}", module.getName(), e.getMessage(), e);
            }
        }
    }

    public int getThreshold() {
        return threshold;
    }
}
