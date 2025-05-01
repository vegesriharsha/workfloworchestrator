package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskStatus;
import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.UserReviewService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * REST controller for user review operations
 */
@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class UserReviewController {

    private final UserReviewService userReviewService;
    private final TaskExecutionService taskExecutionService;

    /**
     * Get review options for a specific review point
     */
    @GetMapping("/{reviewPointId}/options")
    public ReviewOptions getReviewOptions(@PathVariable Long reviewPointId) {
        // Get workflow execution
        WorkflowExecution workflowExecution = userReviewService.getWorkflowExecutionByReviewPointId(reviewPointId);

        // Get the review point
        UserReviewPoint reviewPoint = workflowExecution.getReviewPoints().stream()
                .filter(rp -> rp.getId().equals(reviewPointId))
                .findFirst()
                .orElseThrow(() -> new WorkflowException("Review point not found with id: " + reviewPointId));

        // Get the task execution
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(reviewPoint.getTaskExecutionId());

        // Get all tasks up to current one
        List<TaskExecution> allTasks = taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId());
        List<TaskInfo> priorTasks = new ArrayList<>();

        for (TaskExecution task : allTasks) {
            TaskInfo taskInfo = new TaskInfo(
                    task.getId(),
                    task.getTaskDefinition().getName(),
                    task.getStatus().toString(),
                    task.getCompletedAt() != null ? task.getCompletedAt().toString() : null
            );

            priorTasks.add(taskInfo);

            if (task.getId().equals(taskExecution.getId())) {
                break;
            }
        }

        // Find previous review points
        List<UserReviewPoint> reviewPoints = workflowExecution.getReviewPoints().stream()
                .sorted(Comparator.comparing(UserReviewPoint::getCreatedAt))
                .toList();

        List<ReviewPointInfo> previousReviewPoints = new ArrayList<>();
        for (UserReviewPoint point : reviewPoints) {
            if (point.getId().equals(reviewPointId)) {
                break;
            }

            TaskExecution pointTask = taskExecutionService.getTaskExecution(point.getTaskExecutionId());

            ReviewPointInfo info = new ReviewPointInfo(
                    point.getId(),
                    pointTask.getTaskDefinition().getName(),
                    point.getCreatedAt().toString(),
                    point.getReviewedAt() != null ? point.getReviewedAt().toString() : null,
                    point.getReviewer()
            );

            previousReviewPoints.add(info);
        }

        // Build options
        return new ReviewOptions(
                reviewPointId,
                taskExecution.getTaskDefinition().getName(),
                taskExecution.getStatus().toString(),
                priorTasks,
                previousReviewPoints,
                hasFailedTasks(allTasks, taskExecution)
        );
    }

    /**
     * Submit a review decision
     */
    @PostMapping("/{reviewPointId}")
    public WorkflowExecution submitReview(
            @PathVariable Long reviewPointId,
            @RequestBody EnhancedReviewRequest request) {

        return userReviewService.submitUserReview(
                reviewPointId,
                request.getDecision(),
                request.getReviewer(),
                request.getComment(),
                request.getReplayTaskIds(),
                request.getOverrideStatus());
    }

    private boolean hasFailedTasks(List<TaskExecution> allTasks, TaskExecution currentTask) {
        for (TaskExecution task : allTasks) {
            if (task.getId().equals(currentTask.getId())) {
                return task.getStatus() == TaskStatus.FAILED;
            }
        }
        return false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInfo {
        private Long id;
        private String name;
        private String status;
        private String completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewPointInfo {
        private Long id;
        private String taskName;
        private String createdAt;
        private String reviewedAt;
        private String reviewer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewOptions {
        private Long reviewPointId;
        private String taskName;
        private String taskStatus;
        private List<TaskInfo> priorTasks;
        private List<ReviewPointInfo> previousReviewPoints;
        private boolean isFailedTask;
    }

    @Data
    public static class EnhancedReviewRequest {
        private UserReviewPoint.ReviewDecision decision;
        private String reviewer;
        private String comment;
        private List<Long> replayTaskIds;
        private TaskStatus overrideStatus;
    }
}
