package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing user review operations in workflows
 * Handles review points that require human intervention
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserReviewService {

    private final WorkflowExecutionService workflowExecutionService;
    private final TaskExecutionService taskExecutionService;
    private final WorkflowEngine workflowEngine;
    private final EventPublisherService eventPublisherService;

    /**
     * Submit a review decision for a task
     * Enhanced to support various replay strategies
     *
     * @param reviewPointId the review point ID
     * @param decision the review decision
     * @param reviewer the reviewer's identity
     * @param comment optional comment
     * @param replayTaskIds task IDs to replay (for REPLAY_SUBSET)
     * @param overrideStatus status to override to (for OVERRIDE)
     * @return the updated workflow execution
     */
    @Transactional
    public WorkflowExecution submitUserReview(
            Long reviewPointId,
            UserReviewPoint.ReviewDecision decision,
            String reviewer,
            String comment,
            List<Long> replayTaskIds,
            TaskStatus overrideStatus) {

        WorkflowExecution workflowExecution = getWorkflowExecutionByReviewPointId(reviewPointId);

        UserReviewPoint reviewPoint = workflowExecution.getReviewPoints().stream()
                .filter(rp -> rp.getId().equals(reviewPointId))
                .findFirst()
                .orElseThrow(() -> new WorkflowException("Review point not found with id: " + reviewPointId));

        // Update review point
        reviewPoint.setReviewedAt(LocalDateTime.now());
        reviewPoint.setReviewer(reviewer);
        reviewPoint.setComment(comment);
        reviewPoint.setDecision(decision);

        // Get the task execution being reviewed
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(reviewPoint.getTaskExecutionId());

        // Set replay strategy and related data based on decision
        switch (decision) {
            case APPROVE:
                reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.NONE);
                processApproval(workflowExecution, taskExecution);
                break;

            case REJECT:
                reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.NONE);
                processRejection(workflowExecution, taskExecution, reviewer);
                break;

            case RESTART:
                reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.SINGLE_TASK);
                reviewPoint.getReplayTaskIds().add(taskExecution.getId());
                restartTask(workflowExecution, taskExecution);
                break;

            case REPLAY_SUBSET:
                reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.SELECTED_TASKS);
                reviewPoint.getReplayTaskIds().addAll(replayTaskIds);
                replaySelectedTasks(workflowExecution, replayTaskIds);
                break;

            case REPLAY_ALL:
                reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.ALL_PRIOR_TASKS);
                replayAllPriorTasks(workflowExecution, taskExecution);
                break;

            case REPLAY_FROM_LAST:
                reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.FROM_LAST_REVIEW);
                reviewPoint.setPreviousReviewPointId(findPreviousReviewPointId(workflowExecution, reviewPointId));
                replayFromLastReviewPoint(workflowExecution, reviewPointId);
                break;

            case OVERRIDE:
                reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.NONE);
                reviewPoint.setOverrideStatus(overrideStatus);
                overrideTaskStatus(workflowExecution, taskExecution, overrideStatus);
                break;

            default:
                throw new WorkflowException("Unsupported review decision: " + decision);
        }

        // Save the workflow with updated review point
        workflowExecutionService.save(workflowExecution);

        // Publish event
        eventPublisherService.publishUserReviewCompletedEvent(workflowExecution, reviewPoint);

        return workflowExecution;
    }

    // Implementation of processing methods

    private void processApproval(WorkflowExecution workflowExecution, TaskExecution taskExecution) {
        // Mark task as completed and continue workflow
        taskExecutionService.completeTaskExecution(taskExecution.getId(), taskExecution.getOutputs());
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
        workflowEngine.executeWorkflow(workflowExecution.getId());
    }

    private void processRejection(WorkflowExecution workflowExecution, TaskExecution taskExecution, String reviewer) {
        // Mark task as failed and continue with failure handling
        taskExecutionService.failTaskExecution(taskExecution.getId(), "Rejected by user: " + reviewer);
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
        workflowEngine.executeWorkflow(workflowExecution.getId());
    }

    private void restartTask(WorkflowExecution workflowExecution, TaskExecution taskExecution) {
        // Restart the specific task
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
        workflowEngine.restartTask(workflowExecution.getId(), taskExecution.getId());
    }

    private void replaySelectedTasks(WorkflowExecution workflowExecution, List<Long> taskIds) {
        // Reset selected tasks for replay
        for (Long taskId : taskIds) {
            resetTaskForReplay(taskId);
        }

        // Update workflow status and execute the subset
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
        workflowEngine.executeTaskSubset(workflowExecution.getId(), taskIds);
    }

    private void replayAllPriorTasks(WorkflowExecution workflowExecution, TaskExecution currentTask) {
        // Get all tasks up to current one
        List<TaskExecution> allTasks = taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId());
        List<Long> priorTaskIds = new ArrayList<>();

        for (TaskExecution task : allTasks) {
            priorTaskIds.add(task.getId());
            if (task.getId().equals(currentTask.getId())) {
                break;
            }
        }

        // Reset all prior tasks
        for (Long taskId : priorTaskIds) {
            resetTaskForReplay(taskId);
        }

        // Update workflow status and restart from beginning
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
        workflowExecution.setCurrentTaskIndex(0);
        workflowExecutionService.save(workflowExecution);
        workflowEngine.executeWorkflow(workflowExecution.getId());
    }

    private Long findPreviousReviewPointId(WorkflowExecution workflowExecution, Long currentReviewPointId) {
        // Find the previous review point
        List<UserReviewPoint> reviewPoints = workflowExecution.getReviewPoints().stream()
                .sorted(Comparator.comparing(UserReviewPoint::getCreatedAt))
                .toList();

        Long previousId = null;
        for (UserReviewPoint point : reviewPoints) {
            if (point.getId().equals(currentReviewPointId)) {
                return previousId;
            }
            previousId = point.getId();
        }

        return null;
    }

    private void replayFromLastReviewPoint(WorkflowExecution workflowExecution, Long currentReviewPointId) {
        Long previousReviewPointId = findPreviousReviewPointId(workflowExecution, currentReviewPointId);

        if (previousReviewPointId == null) {
            // No previous review point, replay all
            replayAllPriorTasks(workflowExecution,
                    taskExecutionService.getTaskExecution(
                            workflowExecution.getReviewPoints().stream()
                                    .filter(rp -> rp.getId().equals(currentReviewPointId))
                                    .findFirst()
                                    .orElseThrow()
                                    .getTaskExecutionId()));
            return;
        }

        // Find the task from previous review point
        Long previousTaskId = workflowExecution.getReviewPoints().stream()
                .filter(rp -> rp.getId().equals(previousReviewPointId))
                .findFirst()
                .orElseThrow()
                .getTaskExecutionId();

        TaskExecution previousTask = taskExecutionService.getTaskExecution(previousTaskId);

        // Get all tasks from previous review point to current one
        List<TaskExecution> allTasks = taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId());
        boolean collectTasks = false;
        List<Long> tasksToReplay = new ArrayList<>();

        for (TaskExecution task : allTasks) {
            if (task.getId().equals(previousTask.getId())) {
                collectTasks = true;
            }

            if (collectTasks) {
                tasksToReplay.add(task.getId());
            }
        }

        // Reset tasks and replay
        for (Long taskId : tasksToReplay) {
            resetTaskForReplay(taskId);
        }

        // Find index of previous task
        int taskIndex = -1;
        for (int i = 0; i < allTasks.size(); i++) {
            if (allTasks.get(i).getId().equals(previousTask.getId())) {
                taskIndex = i;
                break;
            }
        }

        // Update workflow and start from previous review point
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
        if (taskIndex >= 0) {
            workflowExecution.setCurrentTaskIndex(taskIndex);
            workflowExecutionService.save(workflowExecution);
        }
        workflowEngine.executeWorkflow(workflowExecution.getId());
    }

    private void overrideTaskStatus(WorkflowExecution workflowExecution, TaskExecution taskExecution, TaskStatus overrideStatus) {
        // Override task status and continue
        Map<String, String> outputs = taskExecution.getOutputs();
        if (outputs == null) {
            outputs = new HashMap<>();
        }
        outputs.put("overridden", "true");
        outputs.put("overriddenBy", taskExecution.getTaskDefinition().getName());
        outputs.put("overrideComment", "Status manually overridden by user review");

        if (overrideStatus == TaskStatus.COMPLETED) {
            taskExecutionService.completeTaskExecution(taskExecution.getId(), outputs);
        } else if (overrideStatus == TaskStatus.FAILED) {
            taskExecutionService.failTaskExecution(taskExecution.getId(), "Status manually set to FAILED by user");
        } else if (overrideStatus == TaskStatus.SKIPPED) {
            // Custom status update for SKIPPED
            taskExecution.setStatus(TaskStatus.SKIPPED);
            taskExecution.setOutputs(outputs);
            taskExecution.setCompletedAt(LocalDateTime.now());
            taskExecutionService.saveTaskExecution(taskExecution);
        }

        // Continue workflow execution
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
        workflowEngine.executeWorkflow(workflowExecution.getId());
    }

    private void resetTaskForReplay(Long taskId) {
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(taskId);
        taskExecution.setStatus(TaskStatus.PENDING);
        taskExecution.setStartedAt(null);
        taskExecution.setCompletedAt(null);
        taskExecution.setErrorMessage(null);
        taskExecution.setRetryCount(0);
        taskExecution.setOutputs(new HashMap<>());
        taskExecutionService.saveTaskExecution(taskExecution);
    }

    public WorkflowExecution getWorkflowExecutionByReviewPointId(Long reviewPointId) {
        return workflowExecutionService.getAllWorkflowExecutions().stream()
                .filter(we -> we.getReviewPoints().stream()
                        .anyMatch(rp -> rp.getId().equals(reviewPointId)))
                .findFirst()
                .orElseThrow(() -> new WorkflowException("No workflow execution found for review point id: " + reviewPointId));
    }
}
