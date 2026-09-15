package com.docdebt.service;

import com.docdebt.entity.CodeModule;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class VolatilityService {

    private final ModuleRepository moduleRepository;
    private final PrSummaryRepository prSummaryRepository;
    private final HealingService healingService;

    @Value("${docdebt.volatility.threshold}")
    private int threshold;

    @Value("${docdebt.volatility.pr-weight}")
    private int prWeight;

    @Value("${docdebt.volatility.day-weight}")
    private int dayWeight;

    public VolatilityService(ModuleRepository moduleRepository,
                              PrSummaryRepository prSummaryRepository,
                              HealingService healingService) {
        this.moduleRepository = moduleRepository;
        this.prSummaryRepository = prSummaryRepository;
        this.healingService = healingService;
    }

    /** Score = (unprocessed PR summaries * prWeight) + (days since last doc update * dayWeight) */
    public int recalculate(CodeModule module) {
        long unprocessed = prSummaryRepository.countByModuleAndProcessedFalse(module);
        long days = ChronoUnit.DAYS.between(module.getLastDocUpdate(), LocalDateTime.now());
        int score = (int) (unprocessed * prWeight + days * dayWeight);
        module.setVolatilityScore(score);
        moduleRepository.save(module);
        return score;
    }

    /** The nightly batch job: recompute every module's score, heal anything over threshold. */
    public void evaluateAll() {
        List<CodeModule> modules = moduleRepository.findAll();
        for (CodeModule module : modules) {
            int score = recalculate(module);
            if (score >= threshold) {
                healingService.heal(module);
            }
        }
    }

    public int getThreshold() {
        return threshold;
    }
}
