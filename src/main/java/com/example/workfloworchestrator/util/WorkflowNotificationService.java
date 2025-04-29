package com.example.workfloworchestrator.util;

import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EmailService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowNotificationService {

    private final TaskExecutionService taskExecutionService;
    private final EmailService emailService;
    //private final UserService userService;

    /**
     * Send notifications for review points
     *
     * @param workflowExecution The workflow execution
     * @param reviewPoint The review point that requires attention
     */
    public void notifyReviewersForReviewPoint(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint) {
        // Get task information
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(reviewPoint.getTaskExecutionId());
        TaskDefinition taskDefinition = taskExecution.getTaskDefinition();

        // Determine reviewers based on task configuration or workflow definition
        List<String> reviewerEmails = determineReviewers(workflowExecution, taskDefinition);

        // Generate review URL for the application
        String reviewUrl = generateReviewUrl(workflowExecution.getId(), reviewPoint.getId());

        // Build notification content
        String subject = String.format("Review Required: %s [%s]",
                taskDefinition.getName(),
                workflowExecution.getWorkflowDefinition().getName());

        StringBuilder body = new StringBuilder();
        body.append("A task requires your review:\n\n");
        body.append("Workflow: ").append(workflowExecution.getWorkflowDefinition().getName()).append("\n");
        body.append("Task: ").append(taskDefinition.getName()).append("\n");
        body.append("Status: ").append(taskExecution.getStatus()).append("\n\n");

        if (taskExecution.getStatus() == TaskStatus.FAILED) {
            body.append("ERROR: ").append(taskExecution.getErrorMessage()).append("\n\n");
            body.append("This task has failed and requires your attention. ");
            body.append("You can restart the task, override its status, or take other actions.\n\n");
        } else {
            body.append("This task requires your review before the workflow can continue.\n\n");
        }

        body.append("Please review this task at: ").append(reviewUrl);

        // Send notifications to all reviewers
        for (String email : reviewerEmails) {
            try {
                emailService.sendEmail(email, subject, body.toString());
                log.info("Sent review notification to {}", email);
            } catch (Exception e) {
                log.error("Failed to send review notification to {}: {}", email, e.getMessage());
            }
        }
    }

    /**
     * Determine reviewers based on configuration
     */
    private List<String> determineReviewers(WorkflowExecution workflowExecution, TaskDefinition taskDefinition) {
        // First check task-specific reviewers
        String reviewersConfig = taskDefinition.getConfiguration().get("reviewers");
        if (reviewersConfig != null && !reviewersConfig.isEmpty()) {
            return Arrays.asList(reviewersConfig.split(","));
        }

        // Then check workflow-level reviewers
//        String workflowReviewers = workflowExecution.getWorkflowDefinition()
//                .getConfiguration().getOrDefault("defaultReviewers", "");
//        if (!workflowReviewers.isEmpty()) {
//            return Arrays.asList(workflowReviewers.split(","));
//        }

        // Fall back to system administrators
        return new ArrayList<>();//userService.getAdministratorEmails();
    }

    /**
     * Generate review URL
     */
    private String generateReviewUrl(Long workflowId, Long reviewPointId) {
        return "/workflow/" + workflowId + "/review/" + reviewPointId;
    }

    /**
     * Send notification about completed review
     */
    public void notifyReviewCompleted(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint) {
        // Get task information
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(reviewPoint.getTaskExecutionId());

        // Get workflow initiator
        String initiator = workflowExecution.getVariables().getOrDefault("initiator", "workflow owner");
        String initiatorEmail = "";//userService.getEmailByUsername(initiator);

        if (initiatorEmail == null || initiatorEmail.isEmpty()) {
            log.warn("Could not find email for workflow initiator: {}", initiator);
            return;
        }

        // Build notification content
        String subject = String.format("Review Completed: %s [%s]",
                taskExecution.getTaskDefinition().getName(),
                workflowExecution.getWorkflowDefinition().getName());

        StringBuilder body = new StringBuilder();
        body.append("A review has been completed for your workflow:\n\n");
        body.append("Workflow: ").append(workflowExecution.getWorkflowDefinition().getName()).append("\n");
        body.append("Task: ").append(taskExecution.getTaskDefinition().getName()).append("\n");
        body.append("Reviewer: ").append(reviewPoint.getReviewer()).append("\n");
        body.append("Decision: ").append(reviewPoint.getDecision()).append("\n\n");

        if (reviewPoint.getComment() != null && !reviewPoint.getComment().isEmpty()) {
            body.append("Comments: ").append(reviewPoint.getComment()).append("\n\n");
        }

        if (reviewPoint.getReplayStrategy() != UserReviewPoint.ReplayStrategy.NONE) {
            body.append("Replay Strategy: ").append(reviewPoint.getReplayStrategy()).append("\n");
        }

        body.append("View workflow status at: /workflow/").append(workflowExecution.getId());

        // Send notification
        try {
            emailService.sendEmail(initiatorEmail, subject, body.toString());
            log.info("Sent review completion notification to {}", initiatorEmail);
        } catch (Exception e) {
            log.error("Failed to send review completion notification: {}", e.getMessage());
        }
    }
}
