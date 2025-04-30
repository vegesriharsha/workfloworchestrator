package com.example.workfloworchestrator.engine.context;

import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for accessing and managing task execution data and operations.
 * This acts as a context holder for task execution-related operations throughout the application.
 */
public interface TaskExecutionContextHolder {

    /**
     * Retrieve a task execution by its ID
     * 
     * @param taskExecutionId The ID of the task execution to retrieve
     * @return The task execution object
     */
    TaskExecution getTaskExecution(Long taskExecutionId);
    
    /**
     * Retrieve a task execution by its ID as an Optional
     * 
     * @param taskExecutionId The ID of the task execution to retrieve
     * @return Optional containing the task execution if found
     */
    Optional<TaskExecution> findTaskExecution(Long taskExecutionId);
    
    /**
     * Get all task executions for a specific workflow
     * 
     * @param workflowExecutionId The workflow execution ID
     * @return List of task executions for the workflow
     */
    List<TaskExecution> getTaskExecutionsForWorkflow(Long workflowExecutionId);
    
    /**
     * Get all tasks with a specific status
     * 
     * @param status The task status to filter by
     * @return List of tasks with the specified status
     */
    List<TaskExecution> getTasksByStatus(TaskStatus status);
    
    /**
     * Create a new task execution
     * 
     * @param workflowExecutionId The workflow execution ID
     * @param taskDefinition The task definition
     * @param inputs The task input parameters
     * @return The created task execution
     */
    TaskExecution createTaskExecution(Long workflowExecutionId, TaskDefinition taskDefinition, Map<String, String> inputs);
    
    /**
     * Start a task execution
     * 
     * @param taskExecutionId The task execution ID
     * @return The updated task execution
     */
    TaskExecution startTaskExecution(Long taskExecutionId);
    
    /**
     * Complete a task execution successfully
     * 
     * @param taskExecutionId The task execution ID
     * @param outputs The task output data
     * @return The updated task execution
     */
    TaskExecution completeTaskExecution(Long taskExecutionId, Map<String, String> outputs);
    
    /**
     * Mark a task execution as failed
     * 
     * @param taskExecutionId The task execution ID
     * @param errorMessage The error message
     * @return The updated task execution
     */
    TaskExecution failTaskExecution(Long taskExecutionId, String errorMessage);
    
    /**
     * Cancel a task execution
     * 
     * @param taskExecutionId The task execution ID
     * @return The updated task execution
     */
    TaskExecution cancelTaskExecution(Long taskExecutionId);
    
    /**
     * Skip a task execution
     * 
     * @param taskExecutionId The task execution ID
     * @param reason The reason for skipping
     * @return The updated task execution
     */
    TaskExecution skipTaskExecution(Long taskExecutionId, String reason);
    
    /**
     * Schedule a task for retry
     * 
     * @param taskExecutionId The task execution ID
     * @param errorMessage The error message
     * @return The updated task execution
     */
    TaskExecution scheduleTaskForRetry(Long taskExecutionId, String errorMessage);
    
    /**
     * Reset a task for replay/retry
     * 
     * @param taskExecutionId The task execution ID
     * @param preserveOutputs Whether to preserve existing outputs
     * @return The updated task execution
     */
    TaskExecution resetTaskForReplay(Long taskExecutionId, boolean preserveOutputs);
    
    /**
     * Override the status of a task
     * 
     * @param taskExecutionId The task execution ID
     * @param newStatus The new status
     * @param overrideReason The reason for the override
     * @param overriddenBy The user who performed the override
     * @return The updated task execution
     */
    TaskExecution overrideTaskStatus(Long taskExecutionId, TaskStatus newStatus, 
                                    String overrideReason, String overriddenBy);
    
    /**
     * Create an execution context for a task
     * 
     * @param taskExecution The task execution
     * @return The execution context
     */
    ExecutionContext createExecutionContext(TaskExecution taskExecution);
    
    /**
     * Save changes to a task execution
     * 
     * @param taskExecution The task execution to save
     * @return The saved task execution
     */
    TaskExecution saveTaskExecution(TaskExecution taskExecution);
    
    /**
     * Add an output value to a task execution
     * 
     * @param taskExecutionId The task execution ID
     * @param key The output key
     * @param value The output value
     * @return The updated task execution
     */
    TaskExecution addTaskOutput(Long taskExecutionId, String key, String value);
    
    /**
     * Add multiple output values to a task execution
     * 
     * @param taskExecutionId The task execution ID
     * @param outputs The map of outputs to add
     * @return The updated task execution
     */
    TaskExecution addTaskOutputs(Long taskExecutionId, Map<String, String> outputs);
    
    /**
     * Check if a task has a specific output key
     * 
     * @param taskExecutionId The task execution ID
     * @param key The output key to check
     * @return True if the output exists
     */
    boolean hasTaskOutput(Long taskExecutionId, String key);
    
    /**
     * Get an output value from a task
     * 
     * @param taskExecutionId The task execution ID
     * @param key The output key
     * @return The output value, or null if not found
     */
    String getTaskOutput(Long taskExecutionId, String key);
    
    /**
     * Check if a task is in one of the provided statuses
     * 
     * @param taskExecutionId The task execution ID
     * @param statuses The statuses to check
     * @return True if the task has one of the statuses
     */
    boolean isTaskInStatus(Long taskExecutionId, TaskStatus... statuses);
}