package com.example.workfloworchestrator.engine;

import com.example.workfloworchestrator.event.TaskEvent;
import com.example.workfloworchestrator.event.WorkflowEvent;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EventPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to break circular dependency between WorkflowEngine and WorkflowExecutionService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngineHelper {

    private final EventPublisherService eventPublisherService;

    /**
     * Create a user review point for a task
     *
     * @param workflowExecution The workflow execution
     * @param taskExecutionId The task execution ID
     * @return The created user review point
     */
    public UserReviewPoint createEnhancedUserReviewPoint(WorkflowExecution workflowExecution, Long taskExecutionId) {
        // Create review point
        UserReviewPoint reviewPoint = new UserReviewPoint();
        reviewPoint.setTaskExecutionId(taskExecutionId);
        reviewPoint.setCreatedAt(LocalDateTime.now());

        // Add review point to workflow execution
        workflowExecution.getReviewPoints().add(reviewPoint);

        // Publish event
        eventPublisherService.publishUserReviewRequestedEvent(workflowExecution, reviewPoint);

        return reviewPoint;
    }

    /**
     * Update workflow status
     *
     * @param workflowExecution The workflow execution to update
     * @param status The new status
     */
    public void updateWorkflowStatus(WorkflowExecution workflowExecution, WorkflowStatus status) {
        workflowExecution.setStatus(status);

        if (status == WorkflowStatus.COMPLETED || status == WorkflowStatus.FAILED) {
            workflowExecution.setCompletedAt(LocalDateTime.now());

            if (status == WorkflowStatus.COMPLETED) {
                eventPublisherService.publishWorkflowCompletedEvent(workflowExecution);
            } else if (status == WorkflowStatus.FAILED) {
                eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
            }
        }
    }

    /**
     * Create task override metadata
     *
     * @param taskExecution The task execution
     * @param originalStatus The original status
     * @param overrideReason The reason for override
     * @param overriddenBy The user who performed the override
     * @return Map of outputs with override metadata
     */
    public Map<String, String> createOverrideMetadata(
            TaskExecution taskExecution,
            TaskStatus originalStatus,
            String overrideReason,
            String overriddenBy) {

        Map<String, String> outputs = taskExecution.getOutputs();
        if (outputs == null) {
            outputs = new HashMap<>();
        }

        outputs.put("overridden", "true");
        outputs.put("overriddenBy", overriddenBy);
        outputs.put("overrideReason", overrideReason);
        outputs.put("originalStatus", originalStatus.toString());
        outputs.put("overrideTimestamp", LocalDateTime.now().toString());

        return outputs;
    }

    /**
     * Publish task override event
     *
     * @param taskExecution The task execution
     * @param originalStatus The original status
     * @param newStatus The new status
     * @param overriddenBy The user who performed the override
     */
    public void publishTaskOverrideEvent(
            TaskExecution taskExecution,
            TaskStatus originalStatus,
            TaskStatus newStatus,
            String overriddenBy) {

        // Create and publish event
        TaskEvent overrideEvent = eventPublisherService.createTaskOverrideEvent(
                taskExecution, originalStatus, newStatus, overriddenBy);
        eventPublisherService.publishEvent(overrideEvent);
    }

    /**
     * Publish workflow replay event
     *
     * @param workflowExecution The workflow execution
     * @param taskIds The task IDs being replayed
     */
    public void publishWorkflowReplayEvent(WorkflowExecution workflowExecution, java.util.List<Long> taskIds) {
        // Create and publish event
        WorkflowEvent replayEvent = eventPublisherService.createWorkflowReplayEvent(workflowExecution, taskIds);
        eventPublisherService.publishEvent(replayEvent);
    }
}
