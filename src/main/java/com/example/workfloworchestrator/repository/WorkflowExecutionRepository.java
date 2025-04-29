package com.example.workfloworchestrator.repository;

import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

    Optional<WorkflowExecution> findByCorrelationId(String correlationId);

    List<WorkflowExecution> findByStatus(WorkflowStatus status);

    List<WorkflowExecution> findByWorkflowDefinitionId(Long workflowDefinitionId);

    @Query("SELECT we FROM WorkflowExecution we WHERE we.status = :status AND we.startedAt < :before")
    List<WorkflowExecution> findStuckExecutions(WorkflowStatus status, LocalDateTime before);

    <E> List<WorkflowExecution> findByStatusIn(List<WorkflowStatus> workflowStatuses);
}
