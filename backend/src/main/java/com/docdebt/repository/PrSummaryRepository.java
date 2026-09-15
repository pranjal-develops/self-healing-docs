package com.docdebt.repository;

import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrSummaryRepository extends JpaRepository<PrSummary, Long> {
    List<PrSummary> findByModuleAndProcessedFalse(CodeModule module);
    long countByModuleAndProcessedFalse(CodeModule module);
}
