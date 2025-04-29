package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for workflow execution strategies
 */
public interface ExecutionStrategy {

    /**
     * Execute the entire workflow
     *
     * @param workflowExecution the workflow execution to process
     * @return CompletableFuture with the final workflow status
     */
    CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution);

    /**
     * Execute a subset of tasks in the workflow
     *
     * @param workflowExecution the workflow execution
     * @param taskIds the IDs of tasks to execute
     * @return CompletableFuture with the final workflow status
     */
    CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List<Long> taskIds);
}
