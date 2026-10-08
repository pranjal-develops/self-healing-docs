package com.docdebt.repository;

import com.docdebt.entity.CodeModule;
import com.docdebt.entity.PrSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrSummaryRepository extends JpaRepository<PrSummary, Long> {
    List<PrSummary> findByModuleAndProcessedFalse(CodeModule module);
    long countByModuleAndProcessedFalse(CodeModule module);

    /** Used for deduplication – check if a delivery was already processed. */
    Optional<PrSummary> findByDeliveryId(String deliveryId);
    boolean existsByDeliveryId(String deliveryId);
}
