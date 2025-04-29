package com.example.workfloworchestrator.repository;

import com.example.workfloworchestrator.model.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

    Optional<WorkflowDefinition> findByNameAndVersion(String name, String version);

    List<WorkflowDefinition> findByNameOrderByVersionDesc(String name);

    Optional<WorkflowDefinition> findFirstByNameOrderByCreatedAtDesc(String name);
}
