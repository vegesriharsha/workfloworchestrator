package com.example.workfloworchestrator.service.api;

import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;

/**
 * Interface for updating workflow execution statuses.
 * Decouples status updates from the WorkflowEngine to avoid circular dependencies.
 */
public interface WorkflowStatusUpdater {
    
    /**
     * Updates the status of a workflow execution.
     *
     * @param workflowExecutionId the ID of the workflow execution to update
     * @param status the new status to set
     * @return the updated workflow execution
     */
    WorkflowExecution updateWorkflowExecutionStatus(Long workflowExecutionId, WorkflowStatus status);
    
    /**
     * Save a workflow execution.
     *
     * @param workflowExecution the workflow execution to save
     * @return the saved workflow execution
     */
    WorkflowExecution save(WorkflowExecution workflowExecution);
    
    /**
     * Get a workflow execution by ID.
     *
     * @param workflowExecutionId the ID of the workflow execution
     * @return the workflow execution
     */
    WorkflowExecution getWorkflowExecution(Long workflowExecutionId);
}