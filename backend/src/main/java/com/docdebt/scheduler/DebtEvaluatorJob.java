package com.docdebt.scheduler;

import com.docdebt.service.VolatilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly job that re-evaluates all modules and auto-heals anything that
 * has crossed the volatility threshold.
 *
 * Scheduling is enabled via @EnableScheduling in AppConfig.
 * Cron expression is configured via docdebt.volatility.schedule-cron.
 */
@Slf4j
@Component
public class DebtEvaluatorJob {

    private final VolatilityService volatilityService;

    public DebtEvaluatorJob(VolatilityService volatilityService) {
        this.volatilityService = volatilityService;
    }

    @Scheduled(cron = "${docdebt.volatility.schedule-cron:0 0 2 * * *}")
    public void run() {
        log.info("Nightly Doc-Debt evaluation starting...");
        try {
            volatilityService.evaluateAll();
        } catch (Exception e) {
            log.error("Nightly evaluation failed unexpectedly: {}", e.getMessage(), e);
        }
        log.info("Nightly Doc-Debt evaluation complete.");
    }
}
