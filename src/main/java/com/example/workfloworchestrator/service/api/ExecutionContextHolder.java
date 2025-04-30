package com.example.workfloworchestrator.service.api;

import com.example.workfloworchestrator.model.ExecutionContext;

/**
 * Interface for managing execution context data.
 * Decouples context operations from workflow engine to avoid circular dependencies.
 */
public interface ExecutionContextHolder {
    
    /**
     * Create a new execution context.
     * 
     * @return a new execution context
     */
    ExecutionContext createExecutionContext();
    
    /**
     * Retrieve the execution context for a specific workflow.
     *
     * @param workflowExecutionId the ID of the workflow execution
     * @return the execution context for the workflow
     */
    ExecutionContext getExecutionContextForWorkflow(Long workflowExecutionId);
    
    /**
     * Store or update an execution context for a workflow.
     *
     * @param workflowExecutionId the ID of the workflow execution
     * @param context the execution context to store
     */
    void setExecutionContextForWorkflow(Long workflowExecutionId, ExecutionContext context);
    
    /**
     * Clear the execution context for a workflow.
     *
     * @param workflowExecutionId the ID of the workflow execution
     */
    void clearExecutionContext(Long workflowExecutionId);
}