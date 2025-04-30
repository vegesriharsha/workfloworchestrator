package com.example.workfloworchestrator.engine;

import com.example.workfloworchestrator.model.TaskStatus;
import com.example.workfloworchestrator.model.UserReviewPoint;

import java.util.List;

/**
 * Interface that defines the workflow execution operations
 * Used to break circular dependency between WorkflowEngine and WorkflowExecutionService
 */
public interface WorkflowExecutor {
    
    /**
     * Execute a workflow asynchronously
     *
     * @param workflowExecutionId the workflow execution ID
     */
    void executeWorkflow(Long workflowExecutionId);
    
    /**
     * Restart a specific task in a workflow
     * Resets the task and continues workflow execution from that point
     *
     * @param workflowExecutionId the workflow execution ID
     * @param taskExecutionId the task execution ID to restart
     */
    void restartTask(Long workflowExecutionId, Long taskExecutionId);
    
    /**
     * Execute a subset of tasks within a workflow
     * Useful for retry scenarios or partial workflow execution
     *
     * @param workflowExecutionId the workflow execution ID
     * @param taskIds the list of task IDs to execute
     */
    void executeTaskSubset(Long workflowExecutionId, List<Long> taskIds);
    
    /**
     * Enhanced task replay functionality
     *
     * @param workflowExecutionId The workflow execution ID
     * @param taskIds The task IDs to replay
     * @param preserveOutputs Whether to preserve task outputs during replay
     */
    void replayTasks(Long workflowExecutionId, List<Long> taskIds, boolean preserveOutputs);
    
    /**
     * Override a task's status
     *
     * @param workflowExecutionId The workflow execution ID
     * @param taskId The task ID
     * @param newStatus The new status
     * @param overrideReason The reason for override
     * @param overriddenBy The user who performed the override
     */
    void overrideTaskStatus(Long workflowExecutionId, Long taskId, TaskStatus newStatus,
                          String overrideReason, String overriddenBy);
    
    /**
     * Create a user review point with enhanced options
     *
     * @param taskExecutionId The task execution ID
     * @return The created user review point
     */
    UserReviewPoint createEnhancedUserReviewPoint(Long taskExecutionId);
}