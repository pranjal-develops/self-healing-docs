package com.docdebt.service;

import com.docdebt.dto.HealResultDto;
import com.docdebt.dto.HealResultDto.DocHealResult;
import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements the "Reduce" phase + Enterprise Bridge from the project plan -
 * run once per doc type (technical, business): gather unprocessed summaries
 * -> semantic discovery (match existing doc or scaffold new one) -> synthesize
 * with Gemini -> publish as an unpublished draft -> clear pending summaries &
 * reset volatility once both docs are done.
 */
@Service
public class HealingService {

    private final PrSummaryRepository prSummaryRepository;
    private final ModuleRepository moduleRepository;
    private final DocStorageService docStorageService;
    private final GeminiService geminiService;
    private final SemanticDiscoveryService semanticDiscoveryService;

    public HealingService(PrSummaryRepository prSummaryRepository,
                           ModuleRepository moduleRepository,
                           DocStorageService docStorageService,
                           GeminiService geminiService,
                           SemanticDiscoveryService semanticDiscoveryService) {
        this.prSummaryRepository = prSummaryRepository;
        this.moduleRepository = moduleRepository;
        this.docStorageService = docStorageService;
        this.geminiService = geminiService;
        this.semanticDiscoveryService = semanticDiscoveryService;
    }

    public HealResultDto heal(CodeModule module) {
        List<PrSummary> unprocessed = prSummaryRepository.findByModuleAndProcessedFalse(module);

        if (unprocessed.isEmpty()) {
            throw new IllegalStateException(
                    "Module '%s' has no pending PR summaries - nothing to heal from. ".formatted(module.getName()) +
                    "Merge a real PR that touches this module, or use the Time Travel button to simulate one first."
            );
        }

        String aggregatedTechnical = unprocessed.stream()
                .map(s -> "- [PR #%s by %s] %s".formatted(s.getPrNumber(), s.getAuthor(), s.getTechnicalSummary()))
                .collect(Collectors.joining("\n"));

        String aggregatedBusiness = unprocessed.stream()
                .map(s -> "- [PR #%s by %s] %s".formatted(s.getPrNumber(), s.getAuthor(), s.getBusinessSummary()))
                .collect(Collectors.joining("\n"));

        DocHealResult technicalResult = healOne(module, DocType.TECHNICAL, aggregatedTechnical);
        DocHealResult businessResult = healOne(module, DocType.BUSINESS, aggregatedBusiness);

        // Clear the queue and reset the score once both docs are healed.
        unprocessed.forEach(s -> s.setProcessed(true));
        prSummaryRepository.saveAll(unprocessed);
        module.setLastDocUpdate(LocalDateTime.now());
        module.setVolatilityScore(0);
        moduleRepository.save(module);

        return new HealResultDto(module.getName(), technicalResult, businessResult, unprocessed.size());
    }

    private DocHealResult healOne(CodeModule module, DocType type, String aggregated) {
        String path = type == DocType.TECHNICAL ? module.getTechnicalDocPath() : module.getBusinessDocPath();

        boolean wasScaffolded;
        String oldContent;
        String newContent;

        if (path != null && !path.isBlank()) {
            // Module already mapped to a known doc of this type - fetch & rewrite it.
            oldContent = docStorageService.getDocumentContent(type, path);
            wasScaffolded = (oldContent == null);
            newContent = wasScaffolded
                    ? scaffold(type, module.getName(), aggregated)
                    : synthesize(type, oldContent, aggregated, module.getName());
        } else {
            // No mapping yet - run semantic discovery against other modules' docs of this type.
            SemanticDiscoveryService.MatchResult match = semanticDiscoveryService.findBestMatch(type, aggregated);
            if (match.match().isPresent()) {
                CodeModule matched = match.match().get();
                String matchedPath = type == DocType.TECHNICAL ? matched.getTechnicalDocPath() : matched.getBusinessDocPath();
                path = matchedPath;
                oldContent = docStorageService.getDocumentContent(type, matchedPath);
                newContent = synthesize(type, oldContent, aggregated, module.getName());
                wasScaffolded = false;
            } else {
                // Auto-Scaffolding pipeline: brand new doc from template.
                oldContent = "";
                newContent = scaffold(type, module.getName(), aggregated);
                path = module.getName() + (type == DocType.TECHNICAL ? "-HLD.md" : "-Business.md");
                wasScaffolded = true;
            }
            applyPath(module, type, path, wasScaffolded);
        }

        String fileName = path != null ? path : module.getName() + (type == DocType.TECHNICAL ? "-HLD.md" : "-Business.md");
        String draftPath = docStorageService.pushDraft(type, fileName, newContent);

        // Index this module's embedding so future modules can be semantically matched to it.
        semanticDiscoveryService.storeEmbedding(type, module, newContent);

        return new DocHealResult(oldContent, newContent, draftPath, wasScaffolded);
    }

    private void applyPath(CodeModule module, DocType type, String path, boolean scaffolded) {
        if (type == DocType.TECHNICAL) {
            module.setTechnicalDocPath(path);
            if (scaffolded) module.setTechnicalScaffolded(true);
        } else {
            module.setBusinessDocPath(path);
            if (scaffolded) module.setBusinessScaffolded(true);
        }
    }

    private String synthesize(DocType type, String existingDoc, String aggregated, String moduleName) {
        return type == DocType.TECHNICAL
                ? geminiService.synthesizeTechnicalDocUpdate(existingDoc, aggregated, moduleName)
                : geminiService.synthesizeBusinessDocUpdate(existingDoc, aggregated, moduleName);
    }

    private String scaffold(DocType type, String moduleName, String aggregated) {
        return type == DocType.TECHNICAL
                ? geminiService.scaffoldNewTechnicalDoc(moduleName, aggregated)
                : geminiService.scaffoldNewBusinessDoc(moduleName, aggregated);
    }
}
