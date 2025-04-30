package com.example.workfloworchestrator.event;

import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskStatus;
import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowExecution;

import java.util.List;

/**
 * Interface to decouple event publishing logic from workflow components.
 * Provides methods for creating and publishing various workflow and task events.
 */
public interface EventPublisherContextHolder {

    /**
     * Publish a workflow started event
     *
     * @param workflowExecution The workflow execution that started
     */
    void publishWorkflowStartedEvent(WorkflowExecution workflowExecution);

    /**
     * Publish a workflow completed event
     *
     * @param workflowExecution The workflow execution that completed
     */
    void publishWorkflowCompletedEvent(WorkflowExecution workflowExecution);

    /**
     * Publish a workflow failed event
     *
     * @param workflowExecution The workflow execution that failed
     */
    void publishWorkflowFailedEvent(WorkflowExecution workflowExecution);

    /**
     * Publish a workflow cancelled event
     *
     * @param workflowExecution The workflow execution that was cancelled
     */
    void publishWorkflowCancelledEvent(WorkflowExecution workflowExecution);

    /**
     * Publish a workflow paused event
     *
     * @param workflowExecution The workflow execution that was paused
     */
    void publishWorkflowPausedEvent(WorkflowExecution workflowExecution);

    /**
     * Publish a workflow resumed event
     *
     * @param workflowExecution The workflow execution that was resumed
     */
    void publishWorkflowResumedEvent(WorkflowExecution workflowExecution);

    /**
     * Publish a workflow status changed event
     *
     * @param workflowExecution The workflow execution that changed status
     */
    void publishWorkflowStatusChangedEvent(WorkflowExecution workflowExecution);

    /**
     * Publish a workflow retry event
     *
     * @param workflowExecution The workflow execution that is being retried
     */
    void publishWorkflowRetryEvent(WorkflowExecution workflowExecution);

    /**
     * Publish a task started event
     *
     * @param taskExecution The task execution that started
     */
    void publishTaskStartedEvent(TaskExecution taskExecution);

    /**
     * Publish a task completed event
     *
     * @param taskExecution The task execution that completed
     */
    void publishTaskCompletedEvent(TaskExecution taskExecution);

    /**
     * Publish a task failed event
     *
     * @param taskExecution The task execution that failed
     */
    void publishTaskFailedEvent(TaskExecution taskExecution);

    /**
     * Publish a user review requested event
     *
     * @param workflowExecution The workflow execution
     * @param reviewPoint The review point
     */
    void publishUserReviewRequestedEvent(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint);

    /**
     * Publish a user review completed event
     *
     * @param workflowExecution The workflow execution
     * @param reviewPoint The review point
     */
    void publishUserReviewCompletedEvent(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint);

    /**
     * Create a workflow event
     *
     * @param workflowExecution The workflow execution
     * @param eventType The event type
     * @return The created event
     */
    WorkflowEvent createWorkflowEvent(WorkflowExecution workflowExecution, WorkflowEventType eventType);

    /**
     * Create a task event
     *
     * @param taskExecution The task execution
     * @param eventType The event type
     * @return The created event
     */
    TaskEvent createTaskEvent(TaskExecution taskExecution, TaskEventType eventType);

    /**
     * Create a user review event
     *
     * @param workflowExecution The workflow execution
     * @param reviewPoint The review point
     * @param eventType The event type
     * @return The created event
     */
    UserReviewEvent createUserReviewEvent(WorkflowExecution workflowExecution,
                                         UserReviewPoint reviewPoint,
                                         UserReviewEventType eventType);

    /**
     * Publish an event
     *
     * @param event The event to publish
     */
    void publishEvent(BaseEvent event);

    /**
     * Create a task event based on task ID
     *
     * @param taskExecutionId The task execution ID
     * @param eventType The event type
     * @return The created event
     */
    TaskEvent createTaskEventById(Long taskExecutionId, TaskEventType eventType);

    /**
     * Create a workflow event based on workflow ID
     *
     * @param workflowExecutionId The workflow execution ID
     * @param eventType The event type
     * @return The created event
     */
    WorkflowEvent createWorkflowEventById(Long workflowExecutionId, WorkflowEventType eventType);

    /**
     * Create a task replay event
     *
     * @param taskExecution The task execution being replayed
     * @param replayStrategy The replay strategy
     * @return The created event
     */
    TaskEvent createTaskReplayEvent(TaskExecution taskExecution, UserReviewPoint.ReplayStrategy replayStrategy);

    /**
     * Create a task override event
     *
     * @param taskExecution The task execution being overridden
     * @param originalStatus The original status
     * @param newStatus The new status
     * @param overriddenBy The user who performed the override
     * @return The created event
     */
    TaskEvent createTaskOverrideEvent(TaskExecution taskExecution,
                                     TaskStatus originalStatus,
                                     TaskStatus newStatus,
                                     String overriddenBy);

    /**
     * Create a workflow replay event
     *
     * @param workflowExecution The workflow execution
     * @param replayTaskIds The IDs of tasks being replayed
     * @return The created event
     */
    WorkflowEvent createWorkflowReplayEvent(WorkflowExecution workflowExecution, List<Long> replayTaskIds);
}