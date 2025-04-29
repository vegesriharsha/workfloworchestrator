package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.util.WorkflowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for integrating task execution with review points
 * Handles determining when reviews are required and processing review outcomes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewWorkflowIntegrationService {

    private final TaskExecutionService taskExecutionService;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowEngine workflowEngine;
    private final ReviewAuditService auditService;
    private final WorkflowNotificationService notificationService;

    @Value("${workflow.review.create-review-points-for-failed-tasks:true}")
    private boolean createReviewPointsForFailedTasks;

    @Value("${workflow.review.task-types-always-requiring-review:}")
    private List<String> taskTypesRequiringReview;

    @Value("${workflow.review.require-review-for-sensitive-tasks:true}")
    private boolean requireReviewForSensitiveTasks;

    /**
     * Determine if a task requires review
     *
     * @param taskExecution The task execution
     * @return true if review is required
     */
    public boolean requiresReview(TaskExecution taskExecution) {
        TaskDefinition taskDefinition = taskExecution.getTaskDefinition();

        // Check explicit review flag
        if (taskDefinition.isRequireUserReview()) {
            return true;
        }

        // Check task type that always requires review
        if (taskTypesRequiringReview.contains(taskDefinition.getType())) {
            return true;
        }

        // Check if this is a sensitive task requiring review
        if (requireReviewForSensitiveTasks) {
            String sensitivityLevel = taskDefinition.getConfiguration().getOrDefault("sensitivityLevel", "");
            if ("HIGH".equalsIgnoreCase(sensitivityLevel)) {
                return true;
            }
        }

        // Special case: failed tasks with special handling configuration
        if (taskExecution.getStatus() == TaskStatus.FAILED) {
            String failureHandling = taskDefinition.getConfiguration().getOrDefault("failureHandling", "");
            return "REVIEW".equalsIgnoreCase(failureHandling);
        }

        return false;
    }

    /**
     * Handle a completed task, triggering review if needed
     *
     * @param taskExecution The completed task execution
     * @return true if review was triggered
     */
    @Transactional
    public boolean handleCompletedTask(TaskExecution taskExecution) {
        if (requiresReview(taskExecution)) {
            // Get the workflow execution
            WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
                    taskExecution.getWorkflowExecutionId());

            // Create review point
            UserReviewPoint reviewPoint = workflowEngine.createEnhancedUserReviewPoint(taskExecution.getId());

            // Send notifications
            notificationService.notifyReviewersForReviewPoint(workflowExecution, reviewPoint);

            log.info("Created review point {} for task {}",
                    reviewPoint.getId(), taskExecution.getId());

            return true;
        }

        return false;
    }

    /**
     * Handle a failed task, creating review point if configured
     *
     * @param taskExecution The failed task execution
     * @return true if review was triggered
     */
    @Transactional
    public boolean handleFailedTask(TaskExecution taskExecution) {
        if (!createReviewPointsForFailedTasks) {
            return false;
        }

        TaskDefinition taskDefinition = taskExecution.getTaskDefinition();

        // Check failure handling strategy
        String failureHandling = taskDefinition.getConfiguration().getOrDefault("failureHandling", "");

        // If explicit REVIEW handling or general review flag
        if ("REVIEW".equalsIgnoreCase(failureHandling) || requiresReview(taskExecution)) {
            // Get workflow execution
            WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
                    taskExecution.getWorkflowExecutionId());

            // Create review point
            UserReviewPoint reviewPoint = workflowEngine.createEnhancedUserReviewPoint(taskExecution.getId());

            // Send notifications
            notificationService.notifyReviewersForReviewPoint(workflowExecution, reviewPoint);

            log.info("Created review point {} for failed task {}",
                    reviewPoint.getId(), taskExecution.getId());

            return true;
        }

        return false;
    }

    /**
     * Get review history for a task
     *
     * @param taskExecutionId The task execution ID
     * @return List of review events
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReviewHistoryForTask(Long taskExecutionId) {
        List<Map<String, Object>> history = new ArrayList<>();

        // Get task execution
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(taskExecutionId);

        // Get workflow execution
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
                taskExecution.getWorkflowExecutionId());

        // Find all review points related to this task
        List<UserReviewPoint> reviewPoints = workflowExecution.getReviewPoints().stream()
                .filter(rp -> rp.getTaskExecutionId().equals(taskExecutionId))
                .collect(Collectors.toList());

        // For each review point, create an event
        for (UserReviewPoint reviewPoint : reviewPoints) {
            // Add review requested event
            Map<String, Object> requestedEvent = new HashMap<>();
            requestedEvent.put("type", "REVIEW_REQUESTED");
            requestedEvent.put("timestamp", reviewPoint.getCreatedAt());
            requestedEvent.put("title", "Review Requested");
            requestedEvent.put("description", "User review was requested for task: " +
                    taskExecution.getTaskDefinition().getName());

            history.add(requestedEvent);

            // If review was completed, add completion event
            if (reviewPoint.getReviewedAt() != null) {
                Map<String, Object> completedEvent = new HashMap<>();
                completedEvent.put("type", getEventTypeFromDecision(reviewPoint.getDecision()));
                completedEvent.put("timestamp", reviewPoint.getReviewedAt());
                completedEvent.put("title", getTitleFromDecision(reviewPoint.getDecision()));
                completedEvent.put("description", getDescriptionFromDecision(reviewPoint.getDecision()));

                // Add properties
                Map<String, String> properties = new HashMap<>();
                properties.put("reviewer", reviewPoint.getReviewer());
                properties.put("decision", reviewPoint.getDecision().toString());

                if (reviewPoint.getComment() != null && !reviewPoint.getComment().isEmpty()) {
                    properties.put("comment", reviewPoint.getComment());
                }

                if (reviewPoint.getReplayStrategy() != null &&
                        reviewPoint.getReplayStrategy() != UserReviewPoint.ReplayStrategy.NONE) {
                    properties.put("replayStrategy", reviewPoint.getReplayStrategy().toString());

                    if (reviewPoint.getReplayTaskIds() != null && !reviewPoint.getReplayTaskIds().isEmpty()) {
                        properties.put("replayTaskCount", String.valueOf(reviewPoint.getReplayTaskIds().size()));
                    }
                }

                if (reviewPoint.getOverrideStatus() != null) {
                    properties.put("originalStatus", taskExecution.getStatus().toString());
                    properties.put("overrideStatus", reviewPoint.getOverrideStatus().toString());
                }

                completedEvent.put("properties", properties);

                history.add(completedEvent);
            }
        }

        return history;
    }

    /**
     * Get event type based on review decision
     */
    private String getEventTypeFromDecision(UserReviewPoint.ReviewDecision decision) {
        switch (decision) {
            case APPROVE:
            case REJECT:
                return "REVIEW_COMPLETED";
            case RESTART:
                return "TASK_RESTARTED";
            case REPLAY_SUBSET:
            case REPLAY_ALL:
            case REPLAY_FROM_LAST:
                return "TASK_REPLAYED";
            case OVERRIDE:
                return "TASK_OVERRIDE";
            default:
                return "REVIEW_COMPLETED";
        }
    }

    /**
     * Get event title based on review decision
     */
    private String getTitleFromDecision(UserReviewPoint.ReviewDecision decision) {
        switch (decision) {
            case APPROVE:
                return "Task Approved";
            case REJECT:
                return "Task Rejected";
            case RESTART:
                return "Task Restarted";
            case REPLAY_SUBSET:
                return "Tasks Replayed";
            case REPLAY_ALL:
                return "All Prior Tasks Replayed";
            case REPLAY_FROM_LAST:
                return "Tasks Replayed From Last Review";
            case OVERRIDE:
                return "Task Status Overridden";
            default:
                return "Review Completed";
        }
    }

    /**
     * Get event description based on review decision
     */
    private String getDescriptionFromDecision(UserReviewPoint.ReviewDecision decision) {
        switch (decision) {
            case APPROVE:
                return "Task was approved and workflow continued.";
            case REJECT:
                return "Task was rejected and workflow failed.";
            case RESTART:
                return "Task was restarted for another execution attempt.";
            case REPLAY_SUBSET:
                return "Selected tasks were replayed.";
            case REPLAY_ALL:
                return "All tasks up to this point were replayed.";
            case REPLAY_FROM_LAST:
                return "Tasks were replayed from the previous review point.";
            case OVERRIDE:
                return "Task status was manually overridden.";
            default:
                return "Review was completed.";
        }
    }
}
