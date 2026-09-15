package com.docdebt.repository;

import com.docdebt.entity.CodeModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModuleRepository extends JpaRepository<CodeModule, Long> {
    Optional<CodeModule> findByName(String name);
}
