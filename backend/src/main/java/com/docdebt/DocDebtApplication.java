package com.docdebt;

import com.docdebt.entity.CodeModule;
import com.docdebt.repository.ModuleRepository;
import com.docdebt.repository.PrSummaryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.Set;

@SpringBootApplication
@EnableScheduling
public class DocDebtApplication {

    private static final Set<String> SPURIOUS_MODULE_NAMES = Set.of("api", "com", "backend", "frontend", "service", "dto", "controller");

    public static void main(String[] args) {
        SpringApplication.run(DocDebtApplication.class, args);
    }

    @Bean
    public CommandLineRunner cleanupSpuriousModules(ModuleRepository moduleRepository, PrSummaryRepository prSummaryRepository) {
        return args -> {
            List<CodeModule> all = moduleRepository.findAll();
            for (CodeModule m : all) {
                if (m.getName() != null && SPURIOUS_MODULE_NAMES.contains(m.getName().trim().toLowerCase())) {
                    System.out.println("[Cleanup] Removing spurious auto-created module entry: " + m.getName());
                    prSummaryRepository.deleteByModule(m);
                    moduleRepository.delete(m);
                }
            }
        };
    }
}
