package com.docdebt.scheduler;

import com.docdebt.service.VolatilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DebtEvaluatorJob {

    private static final Logger log = LoggerFactory.getLogger(DebtEvaluatorJob.class);
    private final VolatilityService volatilityService;

    public DebtEvaluatorJob(VolatilityService volatilityService) {
        this.volatilityService = volatilityService;
    }

    // Cron pulled from docdebt.volatility.schedule-cron (default: nightly 2am)
    @Scheduled(cron = "${docdebt.volatility.schedule-cron}")
    public void run() {
        log.info("Running nightly Doc-Debt evaluation...");
        volatilityService.evaluateAll();
        log.info("Doc-Debt evaluation complete.");
    }
}
