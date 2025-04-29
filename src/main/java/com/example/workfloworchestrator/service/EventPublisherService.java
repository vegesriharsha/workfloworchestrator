package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.event.*;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskStatus;
import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for publishing workflow events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publish a workflow started event
     *
     * @param workflowExecution The workflow execution that started
     */
    public void publishWorkflowStartedEvent(WorkflowExecution workflowExecution) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.STARTED);
        publishEvent(event);
    }

    /**
     * Publish a workflow completed event
     *
     * @param workflowExecution The workflow execution that completed
     */
    public void publishWorkflowCompletedEvent(WorkflowExecution workflowExecution) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.COMPLETED);
        publishEvent(event);
    }

    /**
     * Publish a workflow failed event
     *
     * @param workflowExecution The workflow execution that failed
     */
    public void publishWorkflowFailedEvent(WorkflowExecution workflowExecution) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.FAILED);
        event.setErrorMessage(workflowExecution.getErrorMessage());
        publishEvent(event);
    }

    /**
     * Publish a workflow cancelled event
     *
     * @param workflowExecution The workflow execution that was cancelled
     */
    public void publishWorkflowCancelledEvent(WorkflowExecution workflowExecution) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.CANCELLED);
        publishEvent(event);
    }

    /**
     * Publish a workflow paused event
     *
     * @param workflowExecution The workflow execution that was paused
     */
    public void publishWorkflowPausedEvent(WorkflowExecution workflowExecution) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.PAUSED);
        publishEvent(event);
    }

    /**
     * Publish a workflow resumed event
     *
     * @param workflowExecution The workflow execution that was resumed
     */
    public void publishWorkflowResumedEvent(WorkflowExecution workflowExecution) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.RESUMED);
        publishEvent(event);
    }

    /**
     * Publish a workflow status changed event
     *
     * @param workflowExecution The workflow execution that changed status
     */
    public void publishWorkflowStatusChangedEvent(WorkflowExecution workflowExecution) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.STATUS_CHANGED);
        event.addProperty("status", workflowExecution.getStatus().toString());
        publishEvent(event);
    }

    /**
     * Publish a workflow retry event
     *
     * @param workflowExecution The workflow execution that is being retried
     */
    public void publishWorkflowRetryEvent(WorkflowExecution workflowExecution) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.RETRY);
        event.addProperty("retryCount", String.valueOf(workflowExecution.getRetryCount()));
        publishEvent(event);
    }

    /**
     * Publish a task started event
     *
     * @param taskExecution The task execution that started
     */
    public void publishTaskStartedEvent(TaskExecution taskExecution) {
        TaskEvent event = createTaskEvent(taskExecution, TaskEventType.STARTED);
        publishEvent(event);
    }

    /**
     * Publish a task completed event
     *
     * @param taskExecution The task execution that completed
     */
    public void publishTaskCompletedEvent(TaskExecution taskExecution) {
        TaskEvent event = createTaskEvent(taskExecution, TaskEventType.COMPLETED);
        publishEvent(event);
    }

    /**
     * Publish a task failed event
     *
     * @param taskExecution The task execution that failed
     */
    public void publishTaskFailedEvent(TaskExecution taskExecution) {
        TaskEvent event = createTaskEvent(taskExecution, TaskEventType.FAILED);
        event.setErrorMessage(taskExecution.getErrorMessage());
        publishEvent(event);
    }

    /**
     * Publish a user review requested event
     *
     * @param workflowExecution The workflow execution
     * @param reviewPoint The review point
     */
    public void publishUserReviewRequestedEvent(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint) {
        UserReviewEvent event = createUserReviewEvent(workflowExecution, reviewPoint, UserReviewEventType.REQUESTED);
        publishEvent(event);
    }

    /**
     * Publish a user review completed event
     *
     * @param workflowExecution The workflow execution
     * @param reviewPoint The review point
     */
    public void publishUserReviewCompletedEvent(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint) {
        UserReviewEvent event = createUserReviewEvent(workflowExecution, reviewPoint, UserReviewEventType.COMPLETED);

        // Add review decision and reviewer info
        event.addProperty("decision", reviewPoint.getDecision().toString());
        event.addProperty("reviewer", reviewPoint.getReviewer());

        if (reviewPoint.getReplayStrategy() != null && reviewPoint.getReplayStrategy() != UserReviewPoint.ReplayStrategy.NONE) {
            event.addProperty("replayStrategy", reviewPoint.getReplayStrategy().toString());
        }

        if (reviewPoint.getOverrideStatus() != null) {
            event.addProperty("overrideStatus", reviewPoint.getOverrideStatus().toString());
        }

        publishEvent(event);
    }

    /**
     * Create a workflow event
     *
     * @param workflowExecution The workflow execution
     * @param eventType The event type
     * @return The created event
     */
    public WorkflowEvent createWorkflowEvent(WorkflowExecution workflowExecution, WorkflowEventType eventType) {
        WorkflowEvent event = new WorkflowEvent(this);
        event.setEventType(eventType);
        event.setWorkflowExecutionId(workflowExecution.getId());
        event.setWorkflowDefinitionId(workflowExecution.getWorkflowDefinition().getId());
        event.setWorkflowName(workflowExecution.getWorkflowDefinition().getName());
        event.setWorkflowVersion(workflowExecution.getWorkflowDefinition().getVersion());
        event.setCorrelationId(workflowExecution.getCorrelationId());
        return event;
    }

    /**
     * Create a task event
     *
     * @param taskExecution The task execution
     * @param eventType The event type
     * @return The created event
     */
    public TaskEvent createTaskEvent(TaskExecution taskExecution, TaskEventType eventType) {
        TaskEvent event = new TaskEvent(this);
        event.setEventType(eventType);
        event.setTaskExecutionId(taskExecution.getId());
        event.setTaskDefinitionId(taskExecution.getTaskDefinition().getId());
        event.setTaskName(taskExecution.getTaskDefinition().getName());
        event.setTaskType(taskExecution.getTaskDefinition().getType());
        event.setWorkflowExecutionId(taskExecution.getWorkflowExecutionId());

        // Get workflow information
        // In a real implementation, this would lookup workflow details
        event.setCorrelationId("workflow-correlation-id");

        return event;
    }

    /**
     * Create a user review event
     *
     * @param workflowExecution The workflow execution
     * @param reviewPoint The review point
     * @param eventType The event type
     * @return The created event
     */
    public UserReviewEvent createUserReviewEvent(WorkflowExecution workflowExecution,
                                                 UserReviewPoint reviewPoint,
                                                 UserReviewEventType eventType) {
        UserReviewEvent event = new UserReviewEvent(this);
        event.setEventType(eventType);
        event.setWorkflowExecutionId(workflowExecution.getId());
        event.setWorkflowDefinitionId(workflowExecution.getWorkflowDefinition().getId());
        event.setWorkflowName(workflowExecution.getWorkflowDefinition().getName());
        event.setCorrelationId(workflowExecution.getCorrelationId());
        event.setReviewPointId(reviewPoint.getId());
        event.setTaskExecutionId(reviewPoint.getTaskExecutionId());
        return event;
    }

    /**
     * Publish an event
     *
     * @param event The event to publish
     */
    public void publishEvent(BaseEvent event) {
        log.debug("Publishing event: {}", event.getEventTypeString());
        eventPublisher.publishEvent(event);
    }

    /**
     * Create a task event based on task ID
     *
     * @param taskExecutionId The task execution ID
     * @param eventType The event type
     * @return The created event
     */
    public TaskEvent createTaskEventById(Long taskExecutionId, TaskEventType eventType) {
        // In a real implementation, this would look up the task execution
        TaskEvent event = new TaskEvent(this);
        event.setEventType(eventType);
        event.setTaskExecutionId(taskExecutionId);
        return event;
    }

    /**
     * Create a workflow event based on workflow ID
     *
     * @param workflowExecutionId The workflow execution ID
     * @param eventType The event type
     * @return The created event
     */
    public WorkflowEvent createWorkflowEventById(Long workflowExecutionId, WorkflowEventType eventType) {
        // In a real implementation, this would look up the workflow execution
        WorkflowEvent event = new WorkflowEvent(this);
        event.setEventType(eventType);
        event.setWorkflowExecutionId(workflowExecutionId);
        return event;
    }

    /**
     * Create a task replay event
     *
     * @param taskExecution The task execution being replayed
     * @param replayStrategy The replay strategy
     * @return The created event
     */
    public TaskEvent createTaskReplayEvent(TaskExecution taskExecution, UserReviewPoint.ReplayStrategy replayStrategy) {
        TaskEvent event = createTaskEvent(taskExecution, TaskEventType.REPLAY);
        event.addProperty("replayStrategy", replayStrategy.toString());
        return event;
    }

    /**
     * Create a task override event
     *
     * @param taskExecution The task execution being overridden
     * @param originalStatus The original status
     * @param newStatus The new status
     * @param overriddenBy The user who performed the override
     * @return The created event
     */
    public TaskEvent createTaskOverrideEvent(TaskExecution taskExecution,
                                             TaskStatus originalStatus,
                                             TaskStatus newStatus,
                                             String overriddenBy) {
        TaskEvent event = createTaskEvent(taskExecution, TaskEventType.OVERRIDE);
        event.addProperty("originalStatus", originalStatus.toString());
        event.addProperty("newStatus", newStatus.toString());
        event.addProperty("overriddenBy", overriddenBy);
        return event;
    }

    /**
     * Create a workflow replay event
     *
     * @param workflowExecution The workflow execution
     * @param replayTaskIds The IDs of tasks being replayed
     * @return The created event
     */
    public WorkflowEvent createWorkflowReplayEvent(WorkflowExecution workflowExecution, List<Long> replayTaskIds) {
        WorkflowEvent event = createWorkflowEvent(workflowExecution, WorkflowEventType.REPLAY);
        event.addProperty("replayTaskCount", String.valueOf(replayTaskIds.size()));
        event.addProperty("replayTaskIds", replayTaskIds.toString());
        return event;
    }
}
