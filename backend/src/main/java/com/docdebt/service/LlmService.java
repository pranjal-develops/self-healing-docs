package com.docdebt.service;

public interface LlmService {
    record DualSummary(String technicalSummary, String businessSummary) {}

    DualSummary summarizeDiff(String prTitle, String prBody, String diff);
    String synthesizeTechnicalDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName);
    String synthesizeBusinessDocUpdate(String existingDoc, String aggregatedSummaries, String moduleName);
    String scaffoldNewTechnicalDoc(String moduleName, String aggregatedSummaries);
    String scaffoldNewBusinessDoc(String moduleName, String aggregatedSummaries);
    String analyzeImpact(String prompt);
    float[] embed(String text);
}
