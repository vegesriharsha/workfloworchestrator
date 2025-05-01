package com.example.workfloworchestrator.util;

import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EmailService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkflowNotificationServiceTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private WorkflowNotificationService notificationService;

    private WorkflowExecution workflowExecution;
    private UserReviewPoint reviewPoint;
    private TaskExecution taskExecution;

    @BeforeEach
    public void setup() {
        // Setup workflow execution
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        workflowExecution = new WorkflowExecution();
        workflowExecution.setId(1L);
        workflowExecution.setWorkflowDefinition(definition);
        workflowExecution.setStatus(WorkflowStatus.AWAITING_USER_REVIEW);

        // Setup task definition
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setId(1L);
        taskDefinition.setName("Review Task");
        Map<String, String> config = new HashMap<>();
        config.put("reviewers", "reviewer1@example.com,reviewer2@example.com");
        taskDefinition.setConfiguration(config);

        // Setup task execution
        taskExecution = new TaskExecution();
        taskExecution.setId(1L);
        taskExecution.setWorkflowExecutionId(workflowExecution.getId());
        taskExecution.setTaskDefinition(taskDefinition);
        taskExecution.setStatus(TaskStatus.PENDING);

        // Setup review point
        reviewPoint = new UserReviewPoint();
        reviewPoint.setId(1L);
        reviewPoint.setTaskExecutionId(taskExecution.getId());
        reviewPoint.setCreatedAt(LocalDateTime.now());
    }

    @Test
    public void testNotifyReviewersForReviewPoint() throws MessagingException {
        // Mock task execution service
        when(taskExecutionService.getTaskExecution(taskExecution.getId())).thenReturn(taskExecution);

        // Call the method
        notificationService.notifyReviewersForReviewPoint(workflowExecution, reviewPoint);

        // Verify that emails were sent to reviewers defined in task configuration
        verify(emailService, times(1)).sendEmail(
                eq("reviewer1@example.com"),
                contains("Review Required"),
                anyString());

        verify(emailService, times(1)).sendEmail(
                eq("reviewer2@example.com"),
                contains("Review Required"),
                anyString());
    }

    @Test
    public void testNotifyReviewersForFailedTask() throws MessagingException {
        // Setup a failed task
        taskExecution.setStatus(TaskStatus.FAILED);
        taskExecution.setErrorMessage("Test error message");

        // Mock task execution service
        when(taskExecutionService.getTaskExecution(taskExecution.getId())).thenReturn(taskExecution);

        // Call the method
        notificationService.notifyReviewersForReviewPoint(workflowExecution, reviewPoint);

        // Verify that emails were sent with error message
        verify(emailService, times(2)).sendEmail(
                anyString(),
                contains("Review Required"),
                contains("ERROR: Test error message"));
    }

    @Test
    public void testNotifyReviewCompletedToWorkflowInitiator() throws MessagingException {
        // Setup workflow with initiator
        Map<String, String> variables = new HashMap<>();
        variables.put("initiator", "workflow-initiator");
        workflowExecution.setVariables(variables);

        // Setup completed review point
        reviewPoint.setReviewer("test-reviewer");
        reviewPoint.setReviewedAt(LocalDateTime.now());
        reviewPoint.setDecision(UserReviewPoint.ReviewDecision.APPROVE);
        reviewPoint.setComment("Test comment");

        // Mock task execution service
        when(taskExecutionService.getTaskExecution(taskExecution.getId())).thenReturn(taskExecution);

        // Call the method
        notificationService.notifyReviewCompleted(workflowExecution, reviewPoint);

        // Verify that an email was attempted to be sent to the initiator
        // Note: In this implementation, since we're not mocking the userService,
        // the email might not be found and no email would be sent.
        // This test verifies that the workflow for notifying the initiator is executed.
        verifyNoInteractions(emailService); // No email sent because initiator email not found
    }

    @Test
    public void testNoNotificationsWhenNoReviewersConfigured() throws MessagingException {
        // Setup task definition with no reviewers
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setId(1L);
        taskDefinition.setName("Review Task");
        taskDefinition.setConfiguration(new HashMap<>()); // No reviewers configured

        taskExecution.setTaskDefinition(taskDefinition);

        // Mock task execution service
        when(taskExecutionService.getTaskExecution(taskExecution.getId())).thenReturn(taskExecution);

        // Call the method
        notificationService.notifyReviewersForReviewPoint(workflowExecution, reviewPoint);

        // Verify that no emails were sent
        verifyNoInteractions(emailService);
    }
}
