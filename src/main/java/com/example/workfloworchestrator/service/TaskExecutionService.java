package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.executor.TaskExecutor;
import com.example.workfloworchestrator.event.TaskEventType;
import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.repository.TaskExecutionRepository;
import com.example.workfloworchestrator.util.RetryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing task executions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskExecutionRepository taskExecutionRepository;
    private final Map<String, TaskExecutor> taskExecutors;
    private final EventPublisherService eventPublisherService;
    private final RetryUtil retryUtil;
    private final ReviewWorkflowIntegrationService reviewIntegrationService;

    @Value("${workflow.task.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${workflow.task.execution.timeout-seconds:3600}")
    private int taskTimeoutSeconds;

    /**
     * Get a task execution by ID
     *
     * @param taskExecutionId The task execution ID
     * @return The task execution
     * @throws WorkflowException if the task execution is not found
     */
    @Transactional(readOnly = true)
    public TaskExecution getTaskExecution(Long taskExecutionId) {
        return taskExecutionRepository.findById(taskExecutionId)
                .orElseThrow(() -> new WorkflowException("Task execution not found with id: " + taskExecutionId));
    }

    /**
     * Get all task executions for a workflow
     *
     * @param workflowExecutionId The workflow execution ID
     * @return List of task executions
     */
    @Transactional(readOnly = true)
    public List<TaskExecution> getTaskExecutionsForWorkflow(Long workflowExecutionId) {
        return taskExecutionRepository.findByWorkflowExecutionIdOrderByTaskDefinitionExecutionOrderAsc(workflowExecutionId);
    }

    /**
     * Get tasks that are scheduled for retry
     *
     * @param now The current time
     * @return List of tasks to retry
     */
    @Transactional(readOnly = true)
    public List<TaskExecution> getTasksToRetry(LocalDateTime now) {
        return taskExecutionRepository.findTasksToRetry(TaskStatus.AWAITING_RETRY, now);
    }

    /**
     * Create a new task execution
     *
     * @param workflowExecution The workflow execution
     * @param taskDefinition The task definition
     * @param inputs The task inputs
     * @return The created task execution
     */
    @Transactional
    public TaskExecution createTaskExecution(
            WorkflowExecution workflowExecution,
            TaskDefinition taskDefinition,
            Map<String, String> inputs) {

        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setTaskDefinition(taskDefinition);
        taskExecution.setWorkflowExecutionId(workflowExecution.getId());
        taskExecution.setStatus(TaskStatus.PENDING);
        taskExecution.setExecutionMode(taskDefinition.getExecutionMode());
        taskExecution.setInputs(inputs != null ? inputs : new HashMap<>());
        taskExecution.setOutputs(new HashMap<>());
        taskExecution.setRetryCount(0);

        return taskExecutionRepository.save(taskExecution);
    }

    /**
     * Execute a task asynchronously
     *
     * @param taskExecutionId The task execution ID
     * @return CompletableFuture with the task execution result
     */
    @Async("taskExecutor")
    @Transactional
    public CompletableFuture<TaskExecution> executeTask(Long taskExecutionId) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);

        // Check if task is already completed or failed
        if (taskExecution.getStatus() == TaskStatus.COMPLETED ||
                taskExecution.getStatus() == TaskStatus.FAILED) {
            return CompletableFuture.completedFuture(taskExecution);
        }

        // Update task status to RUNNING
        taskExecution.setStatus(TaskStatus.RUNNING);
        taskExecution.setStartedAt(LocalDateTime.now());
        taskExecution = taskExecutionRepository.save(taskExecution);

        // Publish task started event
        eventPublisherService.publishTaskStartedEvent(taskExecution);

        // Get the appropriate task executor
        TaskExecutor taskExecutor = getTaskExecutor(taskExecution.getTaskDefinition().getType());

        try {
            // Create execution context
            ExecutionContext context = createExecutionContext(taskExecution);

            // Execute the task
            Map<String, Object> result = taskExecutor.execute(taskExecution.getTaskDefinition(), context);

            // Check if task requires review before completion
            if (reviewIntegrationService.requiresReview(taskExecution)) {
                // Save any outputs from the task
                taskExecution.setOutputs(convertOutputsToStringMap(result));
                taskExecution = taskExecutionRepository.save(taskExecution);

                // Create review point - this will pause the workflow
                reviewIntegrationService.handleCompletedTask(taskExecution);

                return CompletableFuture.completedFuture(taskExecution);
            }

            // If no review required, complete the task
            boolean success = result.containsKey("success") ? (Boolean) result.get("success") : true;

            if (success) {
                return CompletableFuture.completedFuture(
                        completeTaskExecution(taskExecution.getId(), convertOutputsToStringMap(result)));
            } else {
                String errorMessage = result.containsKey("error") ?
                        result.get("error").toString() : "Task execution failed with no error message";

                // Check if this failure requires review
                taskExecution.setErrorMessage(errorMessage);
                taskExecution = taskExecutionRepository.save(taskExecution);

                if (reviewIntegrationService.handleFailedTask(taskExecution)) {
                    // Review point created, return current task state
                    return CompletableFuture.completedFuture(taskExecution);
                }

                // Otherwise handle normal failure
                return CompletableFuture.completedFuture(
                        failTaskExecution(taskExecution.getId(), errorMessage));
            }

        } catch (Exception e) {
            log.error("Error executing task {}", taskExecutionId, e);

            // Decide whether to retry or fail
            return handleTaskExecutionError(taskExecution, e);
        }
    }

    /**
     * Complete a task execution
     *
     * @param taskExecutionId The task execution ID
     * @param outputs The task outputs
     * @return The completed task execution
     */
    @Transactional
    public TaskExecution completeTaskExecution(Long taskExecutionId, Map<String, String> outputs) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);

        // Update task status
        taskExecution.setStatus(TaskStatus.COMPLETED);
        taskExecution.setCompletedAt(LocalDateTime.now());

        // Merge any existing outputs with new outputs
        Map<String, String> mergedOutputs = new HashMap<>(taskExecution.getOutputs());
        if (outputs != null) {
            mergedOutputs.putAll(outputs);
        }
        taskExecution.setOutputs(mergedOutputs);

        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);

        // Publish task completed event
        eventPublisherService.publishTaskCompletedEvent(savedExecution);

        return savedExecution;
    }

    /**
     * Fail a task execution
     *
     * @param taskExecutionId The task execution ID
     * @param errorMessage The error message
     * @return The failed task execution
     */
    @Transactional
    public TaskExecution failTaskExecution(Long taskExecutionId, String errorMessage) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);

        // Update task status
        taskExecution.setStatus(TaskStatus.FAILED);
        taskExecution.setCompletedAt(LocalDateTime.now());
        taskExecution.setErrorMessage(errorMessage);

        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);

        // Publish task failed event
        eventPublisherService.publishTaskFailedEvent(savedExecution);

        return savedExecution;
    }

    /**
     * Mark a task for retry
     *
     * @param taskExecutionId The task execution ID
     * @param errorMessage The error message
     * @return The task execution scheduled for retry
     */
    @Transactional
    public TaskExecution scheduleTaskForRetry(Long taskExecutionId, String errorMessage) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);

        // Increment retry count
        int retryCount = taskExecution.getRetryCount() + 1;
        taskExecution.setRetryCount(retryCount);

        // Calculate next retry time with exponential backoff
        LocalDateTime nextRetryAt = retryUtil.calculateNextRetryTime(retryCount);
        taskExecution.setNextRetryAt(nextRetryAt);

        // Update status and error message
        taskExecution.setStatus(TaskStatus.AWAITING_RETRY);
        taskExecution.setErrorMessage(errorMessage);

        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);

        // Publish event for retry scheduled
        eventPublisherService.createTaskEvent(savedExecution, TaskEventType.AWAITING_RETRY);

        return savedExecution;
    }

    /**
     * Save a task execution
     *
     * @param taskExecution The task execution to save
     * @return The saved task execution
     */
    @Transactional
    public TaskExecution saveTaskExecution(TaskExecution taskExecution) {
        return taskExecutionRepository.save(taskExecution);
    }

    /**
     * Create a user review point for a task
     *
     * @param taskExecutionId The task execution ID
     * @return The created review point
     */
    @Transactional
    public UserReviewPoint createUserReviewPoint(Long taskExecutionId) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);

        UserReviewPoint reviewPoint = new UserReviewPoint();
        reviewPoint.setTaskExecutionId(taskExecutionId);
        reviewPoint.setCreatedAt(LocalDateTime.now());

        // Publish task requires review event
        eventPublisherService.createTaskEvent(taskExecution, TaskEventType.REQUIRES_REVIEW);

        return reviewPoint;
    }

    /**
     * Reset a task execution for replay
     *
     * @param taskExecutionId The task execution ID
     * @param preserveOutputs Whether to preserve outputs
     * @return The reset task execution
     */
    @Transactional
    public TaskExecution resetTaskForReplay(Long taskExecutionId, boolean preserveOutputs) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);

        // Keep outputs if requested
        Map<String, String> outputs = preserveOutputs ? taskExecution.getOutputs() : new HashMap<>();

        // Reset execution state
        taskExecution.setStatus(TaskStatus.PENDING);
        taskExecution.setStartedAt(null);
        taskExecution.setCompletedAt(null);
        taskExecution.setErrorMessage(null);
        taskExecution.setRetryCount(0);
        taskExecution.setOutputs(outputs);

        // Save and publish event
        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);
        eventPublisherService.createTaskEvent(savedExecution, TaskEventType.REPLAY);

        return savedExecution;
    }

    /**
     * Override a task's status
     *
     * @param taskExecutionId The task execution ID
     * @param newStatus The new status
     * @param overrideReason The reason for override
     * @param overriddenBy The user who performed the override
     * @return The updated task execution
     */
    @Transactional
    public TaskExecution overrideTaskStatus(Long taskExecutionId, TaskStatus newStatus,
                                            String overrideReason, String overriddenBy) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);
        TaskStatus originalStatus = taskExecution.getStatus();

        // Update outputs with override information
        Map<String, String> outputs = taskExecution.getOutputs();
        if (outputs == null) {
            outputs = new HashMap<>();
        }
        outputs.put("overridden", "true");
        outputs.put("overriddenBy", overriddenBy);
        outputs.put("overrideReason", overrideReason);
        outputs.put("originalStatus", originalStatus.toString());
        outputs.put("overrideTimestamp", LocalDateTime.now().toString());

        // Set completed timestamp
        taskExecution.setCompletedAt(LocalDateTime.now());

        // Update status
        taskExecution.setStatus(newStatus);
        taskExecution.setOutputs(outputs);

        // Save and publish event
        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);
        eventPublisherService.createTaskOverrideEvent(savedExecution, originalStatus, newStatus, overriddenBy);

        return savedExecution;
    }

    /**
     * Handle a task execution error
     *
     * @param taskExecution The task execution
     * @param error The error
     * @return CompletableFuture with the task execution
     */
    private CompletableFuture<TaskExecution> handleTaskExecutionError(TaskExecution taskExecution, Exception error) {
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            errorMessage = "Unknown error: " + error.getClass().getName();
        }

        // Check retry configuration
        Integer maxAttempts = taskExecution.getTaskDefinition().getRetryLimit();
        if (maxAttempts == null) {
            maxAttempts = maxRetryAttempts;
        }

        // Check if we should retry
        if (taskExecution.getRetryCount() < maxAttempts) {
            return CompletableFuture.completedFuture(
                    scheduleTaskForRetry(taskExecution.getId(), errorMessage));
        }

        // Check if this failure requires review
        taskExecution.setErrorMessage(errorMessage);
        taskExecution = taskExecutionRepository.save(taskExecution);

        if (reviewIntegrationService.handleFailedTask(taskExecution)) {
            // Review point created, return current task state
            return CompletableFuture.completedFuture(taskExecution);
        }

        // Max retries reached, fail the task
        return CompletableFuture.completedFuture(
                failTaskExecution(taskExecution.getId(), errorMessage));
    }

    /**
     * Get the appropriate task executor for a task type
     *
     * @param taskType The task type
     * @return The task executor
     * @throws TaskExecutionException if no executor is found for the task type
     */
    private TaskExecutor getTaskExecutor(String taskType) {
        return Optional.ofNullable(taskExecutors.get(taskType))
                .orElseThrow(() -> new TaskExecutionException("No executor found for task type: " + taskType));
    }

    /**
     * Create an execution context from task inputs
     *
     * @param taskExecution The task execution
     * @return The execution context
     */
    private ExecutionContext createExecutionContext(TaskExecution taskExecution) {
        ExecutionContext context = new ExecutionContext();

        // Add task inputs to context
        for (Map.Entry<String, String> entry : taskExecution.getInputs().entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }

        // Add task and workflow metadata
        context.setVariable("taskId", taskExecution.getId());
        context.setVariable("taskName", taskExecution.getTaskDefinition().getName());
        context.setVariable("workflowId", taskExecution.getWorkflowExecutionId());

        return context;
    }

    /**
     * Convert a map of objects to a map of strings
     *
     * @param objectMap The object map
     * @return The string map
     */
    private Map<String, String> convertOutputsToStringMap(Map<String, Object> objectMap) {
        Map<String, String> stringMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            if (entry.getValue() != null) {
                stringMap.put(entry.getKey(), entry.getValue().toString());
            }
        }

        return stringMap;
    }

    /**
     * Get tasks that have a specific status
     *
     * @param status The task status
     * @return List of tasks with the status
     */
    @Transactional(readOnly = true)
    public List<TaskExecution> getTasksByStatus(TaskStatus status) {
        return taskExecutionRepository.findByStatus(status);
    }

    /**
     * Cancel a task execution
     *
     * @param taskExecutionId The task execution ID
     * @return The cancelled task execution
     */
    @Transactional
    public TaskExecution cancelTaskExecution(Long taskExecutionId) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);

        // Update task status
        taskExecution.setStatus(TaskStatus.CANCELLED);
        taskExecution.setCompletedAt(LocalDateTime.now());

        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);

        // Publish event
        eventPublisherService.createTaskEvent(savedExecution, TaskEventType.CANCELLED);

        return savedExecution;
    }

    /**
     * Skip a task execution
     *
     * @param taskExecutionId The task execution ID
     * @param reason The reason for skipping
     * @return The skipped task execution
     */
    @Transactional
    public TaskExecution skipTaskExecution(Long taskExecutionId, String reason) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);

        // Update task status
        taskExecution.setStatus(TaskStatus.SKIPPED);
        taskExecution.setCompletedAt(LocalDateTime.now());

        // Add skip reason to outputs
        Map<String, String> outputs = taskExecution.getOutputs();
        if (outputs == null) {
            outputs = new HashMap<>();
        }
        outputs.put("skipped", "true");
        outputs.put("skipReason", reason);
        taskExecution.setOutputs(outputs);

        return taskExecutionRepository.save(taskExecution);
    }
}
