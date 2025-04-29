package com.example.workfloworchestrator.repository;

import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    List<TaskExecution> findByWorkflowExecutionIdOrderByTaskDefinitionExecutionOrderAsc(Long workflowExecutionId);

    @Query("SELECT te FROM TaskExecution te WHERE te.status = :status AND te.nextRetryAt <= :now")
    List<TaskExecution> findTasksToRetry(TaskStatus status, LocalDateTime now);

    List<TaskExecution> findByStatus(TaskStatus status);
}
