package com.example.workfloworchestrator.engine;

import com.example.workfloworchestrator.engine.strategy.ExecutionStrategy;
import com.example.workfloworchestrator.event.TaskEvent;
import com.example.workfloworchestrator.event.WorkflowEvent;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.event.*;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.example.workfloworchestrator.util.WorkflowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Core workflow engine that orchestrates workflow execution
 * Coordinates different execution strategies and manages workflow state
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine implements WorkflowExecutor {

    private final WorkflowExecutionService workflowExecutionService;
    private final TaskExecutionService taskExecutionService;
    private final EventPublisherService eventPublisherService;
    private final Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> executionStrategies;
    private final WorkflowNotificationService notificationService;

    /**
     * Execute a workflow asynchronously
     *
     * @param workflowExecutionId the workflow execution ID
     */
    @Async("taskExecutor")
    @Transactional
    public void executeWorkflow(Long workflowExecutionId) {
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);

        try {
            // Check if workflow is in a valid state to execute
            if (workflowExecution.getStatus() != WorkflowStatus.CREATED &&
                    workflowExecution.getStatus() != WorkflowStatus.RUNNING) {
                log.info("Workflow {} cannot be executed in current state: {}",
                        workflowExecutionId, workflowExecution.getStatus());
                return;
            }

            // Set status to RUNNING if not already
            if (workflowExecution.getStatus() == WorkflowStatus.CREATED) {
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.RUNNING);
                eventPublisherService.publishWorkflowStartedEvent(workflowExecution);
            }

            // Get the appropriate execution strategy
            WorkflowDefinition workflowDefinition = workflowExecution.getWorkflowDefinition();
            ExecutionStrategy strategy = getExecutionStrategy(workflowDefinition.getStrategyType());

            // Execute workflow using the selected strategy
            CompletableFuture<WorkflowStatus> futureStatus = strategy.execute(workflowExecution);

            futureStatus.thenAccept(status -> {
                // Update workflow status based on execution result
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, status);

                if (status == WorkflowStatus.COMPLETED) {
                    eventPublisherService.publishWorkflowCompletedEvent(workflowExecution);
                } else if (status == WorkflowStatus.FAILED) {
                    eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
                }
            });

        } catch (Exception e) {
            // Handle unexpected errors
            log.error("Error executing workflow {}", workflowExecutionId, e);
            workflowExecution.setErrorMessage(e.getMessage());
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.FAILED);
            eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
        }
    }

    /**
     * Restart a specific task in a workflow
     * Resets the task and continues workflow execution from that point
     *
     * @param workflowExecutionId the workflow execution ID
     * @param taskExecutionId the task execution ID to restart
     */
    @Async("taskExecutor")
    @Transactional
    public void restartTask(Long workflowExecutionId, Long taskExecutionId) {
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(taskExecutionId);

        try {
            // Update workflow status
            workflowExecution.setStatus(WorkflowStatus.RUNNING);
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.RUNNING);

            // Reset task for execution
            taskExecution.setStatus(TaskStatus.PENDING);
            taskExecution.setStartedAt(null);
            taskExecution.setCompletedAt(null);
            taskExecution.setErrorMessage(null);
            taskExecution.setRetryCount(0);
            taskExecution.setOutputs(new HashMap<>());

            // Save task changes
            taskExecutionService.saveTaskExecution(taskExecution);

            // Set current task index in workflow to point to this task
            int taskIndex = getTaskIndex(workflowExecution, taskExecution);
            if (taskIndex >= 0) {
                workflowExecution.setCurrentTaskIndex(taskIndex);
                workflowExecutionService.save(workflowExecution);
            }

            // Continue workflow execution
            executeWorkflow(workflowExecutionId);

        } catch (Exception e) {
            log.error("Error restarting task {} in workflow {}", taskExecutionId, workflowExecutionId, e);
            workflowExecution.setErrorMessage(e.getMessage());
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.FAILED);
            eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
        }
    }

    /**
     * Execute a subset of tasks within a workflow
     * Useful for retry scenarios or partial workflow execution
     *
     * @param workflowExecutionId the workflow execution ID
     * @param taskIds the list of task IDs to execute
     */
    @Async("taskExecutor")
    @Transactional
    public void executeTaskSubset(Long workflowExecutionId, List<Long> taskIds) {
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);

        try {
            // Update workflow status
            workflowExecution.setStatus(WorkflowStatus.RUNNING);
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.RUNNING);

            // Get the appropriate execution strategy
            WorkflowDefinition workflowDefinition = workflowExecution.getWorkflowDefinition();
            ExecutionStrategy strategy = getExecutionStrategy(workflowDefinition.getStrategyType());

            // Execute the subset of tasks
            CompletableFuture<WorkflowStatus> futureStatus = strategy.executeSubset(workflowExecution, taskIds);

            futureStatus.thenAccept(status -> {
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, status);

                if (status == WorkflowStatus.COMPLETED) {
                    eventPublisherService.publishWorkflowCompletedEvent(workflowExecution);
                } else if (status == WorkflowStatus.FAILED) {
                    eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
                }
            });

        } catch (Exception e) {
            log.error("Error executing task subset for workflow {}", workflowExecutionId, e);
            workflowExecution.setErrorMessage(e.getMessage());
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.FAILED);
            eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
        }
    }

    /**
     * Get the appropriate execution strategy based on type
     * Falls back to sequential strategy if requested type not found
     *
     * @param strategyType the execution strategy type
     * @return the execution strategy
     * @throws WorkflowException if no strategy can be found
     */
    private ExecutionStrategy getExecutionStrategy(WorkflowDefinition.ExecutionStrategyType strategyType) {
        ExecutionStrategy strategy = executionStrategies.get(strategyType);
        if (strategy == null) {
            log.warn("No execution strategy found for type: {}, using sequential strategy", strategyType);
            strategy = executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        }
        if (strategy == null) {
            throw new WorkflowException("No execution strategy available");
        }
        return strategy;
    }

    /**
     * Get the index of a task within a workflow
     *
     * @param workflowExecution the workflow execution
     * @param taskExecution the task execution
     * @return the index of the task, or -1 if not found
     */
    private int getTaskIndex(WorkflowExecution workflowExecution, TaskExecution taskExecution) {
        List<TaskExecution> taskExecutions = taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId());
        for (int i = 0; i < taskExecutions.size(); i++) {
            if (taskExecutions.get(i).getId().equals(taskExecution.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Enhanced task replay functionality
     *
     * @param workflowExecutionId The workflow execution ID
     * @param taskIds The task IDs to replay
     * @param preserveOutputs Whether to preserve task outputs during replay
     */
    @Async("taskExecutor")
    @Transactional
    public void replayTasks(Long workflowExecutionId, List<Long> taskIds, boolean preserveOutputs) {
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);

        try {
            // Update workflow status
            workflowExecution.setStatus(WorkflowStatus.RUNNING);
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.RUNNING);

            // Reset selected tasks
            for (Long taskId : taskIds) {
                resetTaskForReplay(taskId, preserveOutputs);
            }

            // Find the minimum task index
            int minTaskIndex = findMinimumTaskIndex(workflowExecution, taskIds);
            if (minTaskIndex >= 0) {
                workflowExecution.setCurrentTaskIndex(minTaskIndex);
                workflowExecutionService.save(workflowExecution);
            }

            // Log the replay action
            log.info("Replaying tasks {} for workflow {}", taskIds, workflowExecutionId);

            // Create replay event
            Map<String, String> eventProperties = new HashMap<>();
            eventProperties.put("replayTaskIds", taskIds.toString());
            eventProperties.put("preserveOutputs", String.valueOf(preserveOutputs));
            WorkflowEvent replayEvent = eventPublisherService.createWorkflowEvent(
                    workflowExecution, WorkflowEventType.REPLAY);
            replayEvent.setProperties(eventProperties);
            eventPublisherService.publishEvent(replayEvent);

            // Execute workflow starting from the selected tasks
            executeTaskSubset(workflowExecution.getId(), taskIds);

        } catch (Exception e) {
            log.error("Error replaying tasks for workflow {}", workflowExecutionId, e);
            workflowExecution.setErrorMessage(e.getMessage());
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.FAILED);
            eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
        }
    }

    /**
     * Reset a task for replay
     *
     * @param taskId The task ID
     * @param preserveOutputs Whether to preserve the task's outputs
     */
    private void resetTaskForReplay(Long taskId, boolean preserveOutputs) {
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(taskId);

        // Keep outputs if requested
        Map<String, String> outputs = preserveOutputs ? taskExecution.getOutputs() : new HashMap<>();

        // Reset execution state
        taskExecution.setStatus(TaskStatus.PENDING);
        taskExecution.setStartedAt(null);
        taskExecution.setCompletedAt(null);
        taskExecution.setErrorMessage(null);
        taskExecution.setRetryCount(0);
        taskExecution.setOutputs(outputs);

        // Save changes
        taskExecutionService.saveTaskExecution(taskExecution);
    }

    /**
     * Find the minimum task index from a list of task IDs
     *
     * @param workflowExecution The workflow execution
     * @param taskIds The task IDs
     * @return The minimum task index
     */
    private int findMinimumTaskIndex(WorkflowExecution workflowExecution, List<Long> taskIds) {
        List<TaskExecution> allTasks = taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId());
        int minIndex = Integer.MAX_VALUE;

        for (int i = 0; i < allTasks.size(); i++) {
            if (taskIds.contains(allTasks.get(i).getId())) {
                minIndex = Math.min(minIndex, i);
            }
        }

        return minIndex < Integer.MAX_VALUE ? minIndex : -1;
    }

    /**
     * Override a task's status
     *
     * @param workflowExecutionId The workflow execution ID
     * @param taskId The task ID
     * @param newStatus The new status
     * @param overrideReason The reason for override
     * @param overriddenBy The user who performed the override
     */
    @Async("taskExecutor")
    @Transactional
    public void overrideTaskStatus(Long workflowExecutionId, Long taskId, TaskStatus newStatus,
                                   String overrideReason, String overriddenBy) {

        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(taskId);

        try {
            // Update workflow status
            workflowExecution.setStatus(WorkflowStatus.RUNNING);
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.RUNNING);

            // Update task outputs with override information
            Map<String, String> outputs = taskExecution.getOutputs();
            if (outputs == null) {
                outputs = new HashMap<>();
            }
            outputs.put("overridden", "true");
            outputs.put("overriddenBy", overriddenBy);
            outputs.put("overrideReason", overrideReason);
            outputs.put("originalStatus", taskExecution.getStatus().toString());
            outputs.put("overrideTimestamp", LocalDateTime.now().toString());

            // Set completed timestamp
            taskExecution.setCompletedAt(LocalDateTime.now());

            // Apply status-specific updates
            switch (newStatus) {
                case COMPLETED:
                    taskExecution.setStatus(TaskStatus.COMPLETED);
                    break;
                case FAILED:
                    taskExecution.setStatus(TaskStatus.FAILED);
                    taskExecution.setErrorMessage("Status manually set to FAILED by " + overriddenBy + ": " + overrideReason);
                    break;
                case SKIPPED:
                    taskExecution.setStatus(TaskStatus.SKIPPED);
                    break;
                default:
                    throw new IllegalArgumentException("Cannot override to status: " + newStatus);
            }

            // Update outputs
            taskExecution.setOutputs(outputs);
            taskExecutionService.saveTaskExecution(taskExecution);

            // Log the override action
            log.info("Task {} status overridden to {} by {}", taskId, newStatus, overriddenBy);

            // Create override event
            Map<String, String> eventProperties = new HashMap<>();
            eventProperties.put("taskId", taskId.toString());
            eventProperties.put("newStatus", newStatus.toString());
            eventProperties.put("overriddenBy", overriddenBy);
            eventProperties.put("overrideReason", overrideReason);

            TaskEvent overrideEvent = eventPublisherService.createTaskEvent(
                    taskExecution, TaskEventType.OVERRIDE);
            overrideEvent.setProperties(eventProperties);
            eventPublisherService.publishEvent(overrideEvent);

            // Continue workflow execution
            executeWorkflow(workflowExecution.getId());

        } catch (Exception e) {
            log.error("Error overriding task status for task {} in workflow {}",
                    taskId, workflowExecutionId, e);
            workflowExecution.setErrorMessage(e.getMessage());
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.FAILED);
            eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
        }
    }

    /**
     * Create a user review point with enhanced options
     *
     * @param taskExecutionId The task execution ID
     * @return The created user review point
     */
    @Transactional
    public UserReviewPoint createEnhancedUserReviewPoint(Long taskExecutionId) {
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(taskExecutionId);
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(taskExecution.getWorkflowExecutionId());

        // Set workflow to awaiting review status
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.AWAITING_USER_REVIEW);

        // Create review point
        UserReviewPoint reviewPoint = new UserReviewPoint();
        reviewPoint.setTaskExecutionId(taskExecutionId);
        reviewPoint.setCreatedAt(LocalDateTime.now());

        // Add review point to workflow execution
        workflowExecution.getReviewPoints().add(reviewPoint);
        workflowExecutionService.save(workflowExecution);

        // Publish event
        eventPublisherService.publishUserReviewRequestedEvent(workflowExecution, reviewPoint);

        // Send notifications
        notificationService.notifyReviewersForReviewPoint(workflowExecution, reviewPoint);

        return reviewPoint;
    }
}
