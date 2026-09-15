package com.docdebt.dto;

import com.docdebt.entity.CodeModule;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ModuleStatusDto(
        Long id,
        String name,
        int volatilityScore,
        long unprocessedSummaries,
        long daysSinceLastUpdate,
        String heatLevel, // "LOW" | "MEDIUM" | "HIGH"
        boolean technicalScaffolded,
        boolean businessScaffolded
) {
    public static ModuleStatusDto from(CodeModule module, long unprocessedCount, int threshold) {
        long days = ChronoUnit.DAYS.between(module.getLastDocUpdate(), LocalDateTime.now());
        String heat;
        if (module.getVolatilityScore() >= threshold) {
            heat = "HIGH";
        } else if (module.getVolatilityScore() >= threshold / 2) {
            heat = "MEDIUM";
        } else {
            heat = "LOW";
        }
        return new ModuleStatusDto(
                module.getId(),
                module.getName(),
                module.getVolatilityScore(),
                unprocessedCount,
                days,
                heat,
                module.isTechnicalScaffolded(),
                module.isBusinessScaffolded()
        );
    }
}
