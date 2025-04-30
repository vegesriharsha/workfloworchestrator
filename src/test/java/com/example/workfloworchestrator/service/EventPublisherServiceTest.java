package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.event.*;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.TaskStatus;
import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventPublisherServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EventPublisherService eventPublisherService;

    @Captor
    private ArgumentCaptor<BaseEvent> eventCaptor;

    private WorkflowExecution workflowExecution;
    private TaskExecution taskExecution;
    private UserReviewPoint reviewPoint;

    @BeforeEach
    void setUp() {
        // Create test workflow definition
        WorkflowDefinition workflowDefinition = WorkflowDefinition.builder()
                .id(1L)
                .name("TestWorkflow")
                .description("Test workflow")
                .version("1.0")
                .build();

        // Create test workflow execution
        workflowExecution = WorkflowExecution.builder()
                .id(1L)
                .workflowDefinition(workflowDefinition)
                .correlationId("test-correlation-id")
                .status(WorkflowStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();

        // Create test task definition
        TaskDefinition taskDefinition = TaskDefinition.builder()
                .id(2L)
                .name("TestTask")
                .type("REST")
                .build();

        // Create test task execution
        taskExecution = TaskExecution.builder()
                .id(3L)
                .taskDefinition(taskDefinition)
                .workflowExecutionId(workflowExecution.getId())
                .status(TaskStatus.RUNNING)
                .inputs(new HashMap<>())
                .outputs(new HashMap<>())
                .build();

        // Create test review point
        reviewPoint = UserReviewPoint.builder()
                .id(4L)
                .taskExecutionId(taskExecution.getId())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void publishWorkflowStartedEvent_shouldCreateAndPublishEvent() {
        // Act
        eventPublisherService.publishWorkflowStartedEvent(workflowExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof WorkflowEvent);
        
        WorkflowEvent workflowEvent = (WorkflowEvent) capturedEvent;
        assertEquals(WorkflowEventType.STARTED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
    }

    @Test
    void publishWorkflowCompletedEvent_shouldCreateAndPublishEvent() {
        // Act
        eventPublisherService.publishWorkflowCompletedEvent(workflowExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof WorkflowEvent);
        
        WorkflowEvent workflowEvent = (WorkflowEvent) capturedEvent;
        assertEquals(WorkflowEventType.COMPLETED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
    }

    @Test
    void publishWorkflowFailedEvent_shouldCreateAndPublishEventWithErrorMessage() {
        // Arrange
        workflowExecution.setErrorMessage("Test error message");

        // Act
        eventPublisherService.publishWorkflowFailedEvent(workflowExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof WorkflowEvent);
        
        WorkflowEvent workflowEvent = (WorkflowEvent) capturedEvent;
        assertEquals(WorkflowEventType.FAILED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
        assertEquals("Test error message", workflowEvent.getErrorMessage());
    }

    @Test
    void publishWorkflowCancelledEvent_shouldCreateAndPublishEvent() {
        // Act
        eventPublisherService.publishWorkflowCancelledEvent(workflowExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof WorkflowEvent);
        
        WorkflowEvent workflowEvent = (WorkflowEvent) capturedEvent;
        assertEquals(WorkflowEventType.CANCELLED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
    }

    @Test
    void publishWorkflowPausedEvent_shouldCreateAndPublishEvent() {
        // Act
        eventPublisherService.publishWorkflowPausedEvent(workflowExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof WorkflowEvent);
        
        WorkflowEvent workflowEvent = (WorkflowEvent) capturedEvent;
        assertEquals(WorkflowEventType.PAUSED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
    }

    @Test
    void publishWorkflowResumedEvent_shouldCreateAndPublishEvent() {
        // Act
        eventPublisherService.publishWorkflowResumedEvent(workflowExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof WorkflowEvent);
        
        WorkflowEvent workflowEvent = (WorkflowEvent) capturedEvent;
        assertEquals(WorkflowEventType.RESUMED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
    }

    @Test
    void publishWorkflowStatusChangedEvent_shouldCreateAndPublishEventWithStatusProperty() {
        // Arrange
        workflowExecution.setStatus(WorkflowStatus.COMPLETED);

        // Act
        eventPublisherService.publishWorkflowStatusChangedEvent(workflowExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof WorkflowEvent);
        
        WorkflowEvent workflowEvent = (WorkflowEvent) capturedEvent;
        assertEquals(WorkflowEventType.STATUS_CHANGED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
        assertEquals("COMPLETED", workflowEvent.getProperties().get("status"));
    }

    @Test
    void publishWorkflowRetryEvent_shouldCreateAndPublishEventWithRetryCountProperty() {
        // Arrange
        workflowExecution.setRetryCount(3);

        // Act
        eventPublisherService.publishWorkflowRetryEvent(workflowExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof WorkflowEvent);
        
        WorkflowEvent workflowEvent = (WorkflowEvent) capturedEvent;
        assertEquals(WorkflowEventType.RETRY, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
        assertEquals("3", workflowEvent.getProperties().get("retryCount"));
    }

    @Test
    void publishTaskStartedEvent_shouldCreateAndPublishEvent() {
        // Act
        eventPublisherService.publishTaskStartedEvent(taskExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof TaskEvent);
        
        TaskEvent taskEvent = (TaskEvent) capturedEvent;
        assertEquals(TaskEventType.STARTED, taskEvent.getEventType());
        assertEquals(taskExecution.getId(), taskEvent.getTaskExecutionId());
    }

    @Test
    void publishTaskCompletedEvent_shouldCreateAndPublishEvent() {
        // Act
        eventPublisherService.publishTaskCompletedEvent(taskExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof TaskEvent);
        
        TaskEvent taskEvent = (TaskEvent) capturedEvent;
        assertEquals(TaskEventType.COMPLETED, taskEvent.getEventType());
        assertEquals(taskExecution.getId(), taskEvent.getTaskExecutionId());
    }

    @Test
    void publishTaskFailedEvent_shouldCreateAndPublishEventWithErrorMessage() {
        // Arrange
        taskExecution.setErrorMessage("Task error message");

        // Act
        eventPublisherService.publishTaskFailedEvent(taskExecution);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof TaskEvent);
        
        TaskEvent taskEvent = (TaskEvent) capturedEvent;
        assertEquals(TaskEventType.FAILED, taskEvent.getEventType());
        assertEquals(taskExecution.getId(), taskEvent.getTaskExecutionId());
        assertEquals("Task error message", taskEvent.getErrorMessage());
    }

    @Test
    void publishUserReviewRequestedEvent_shouldCreateAndPublishEvent() {
        // Act
        eventPublisherService.publishUserReviewRequestedEvent(workflowExecution, reviewPoint);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof UserReviewEvent);
        
        UserReviewEvent reviewEvent = (UserReviewEvent) capturedEvent;
        assertEquals(UserReviewEventType.REQUESTED, reviewEvent.getEventType());
        assertEquals(workflowExecution.getId(), reviewEvent.getWorkflowExecutionId());
        assertEquals(reviewPoint.getId(), reviewEvent.getReviewPointId());
    }

    @Test
    void publishUserReviewCompletedEvent_shouldCreateAndPublishEventWithBasicProperties() {
        // Arrange
        reviewPoint.setDecision(UserReviewPoint.ReviewDecision.APPROVE);
        reviewPoint.setReviewer("test-user");

        // Act
        eventPublisherService.publishUserReviewCompletedEvent(workflowExecution, reviewPoint);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        BaseEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent instanceof UserReviewEvent);
        
        UserReviewEvent reviewEvent = (UserReviewEvent) capturedEvent;
        assertEquals(UserReviewEventType.COMPLETED, reviewEvent.getEventType());
        assertEquals(workflowExecution.getId(), reviewEvent.getWorkflowExecutionId());
        assertEquals(reviewPoint.getId(), reviewEvent.getReviewPointId());
        assertEquals("APPROVE", reviewEvent.getProperties().get("decision"));
        assertEquals("test-user", reviewEvent.getProperties().get("reviewer"));
    }
    
    @Test
    void publishUserReviewCompletedEvent_withReplayStrategy_shouldIncludeReplayStrategyProperty() {
        // Arrange
        reviewPoint.setDecision(UserReviewPoint.ReviewDecision.REPLAY_SUBSET);
        reviewPoint.setReviewer("test-user");
        reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.SELECTED_TASKS);

        // Act
        eventPublisherService.publishUserReviewCompletedEvent(workflowExecution, reviewPoint);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        UserReviewEvent reviewEvent = (UserReviewEvent) eventCaptor.getValue();
        assertEquals("SELECTED_TASKS", reviewEvent.getProperties().get("replayStrategy"));
    }
    
    @Test
    void publishUserReviewCompletedEvent_withNoReplayStrategy_shouldNotIncludeReplayStrategyProperty() {
        // Arrange
        reviewPoint.setDecision(UserReviewPoint.ReviewDecision.APPROVE);
        reviewPoint.setReviewer("test-user");
        reviewPoint.setReplayStrategy(null);

        // Act
        eventPublisherService.publishUserReviewCompletedEvent(workflowExecution, reviewPoint);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        UserReviewEvent reviewEvent = (UserReviewEvent) eventCaptor.getValue();
        assertFalse(reviewEvent.getProperties().containsKey("replayStrategy"));
    }
    
    @Test
    void publishUserReviewCompletedEvent_withNoneReplayStrategy_shouldNotIncludeReplayStrategyProperty() {
        // Arrange
        reviewPoint.setDecision(UserReviewPoint.ReviewDecision.APPROVE);
        reviewPoint.setReviewer("test-user");
        reviewPoint.setReplayStrategy(UserReviewPoint.ReplayStrategy.NONE);

        // Act
        eventPublisherService.publishUserReviewCompletedEvent(workflowExecution, reviewPoint);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        UserReviewEvent reviewEvent = (UserReviewEvent) eventCaptor.getValue();
        assertFalse(reviewEvent.getProperties().containsKey("replayStrategy"));
    }
    
    @Test
    void publishUserReviewCompletedEvent_withOverrideStatus_shouldIncludeOverrideStatusProperty() {
        // Arrange
        reviewPoint.setDecision(UserReviewPoint.ReviewDecision.OVERRIDE);
        reviewPoint.setReviewer("test-user");
        reviewPoint.setOverrideStatus(TaskStatus.COMPLETED);

        // Act
        eventPublisherService.publishUserReviewCompletedEvent(workflowExecution, reviewPoint);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        UserReviewEvent reviewEvent = (UserReviewEvent) eventCaptor.getValue();
        assertEquals("COMPLETED", reviewEvent.getProperties().get("overrideStatus"));
    }
    
    @Test
    void createWorkflowEvent_shouldPopulateAllFields() {
        // Act
        WorkflowEvent workflowEvent = eventPublisherService.createWorkflowEvent(
                workflowExecution, WorkflowEventType.STARTED);

        // Assert
        assertNotNull(workflowEvent);
        assertEquals(WorkflowEventType.STARTED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
        assertEquals(workflowExecution.getWorkflowDefinition().getId(), workflowEvent.getWorkflowDefinitionId());
        assertEquals(workflowExecution.getWorkflowDefinition().getName(), workflowEvent.getWorkflowName());
        assertEquals(workflowExecution.getWorkflowDefinition().getVersion(), workflowEvent.getWorkflowVersion());
        assertEquals(workflowExecution.getCorrelationId(), workflowEvent.getCorrelationId());
    }

    @Test
    void createTaskEvent_shouldPopulateAllFields() {
        // Act
        TaskEvent taskEvent = eventPublisherService.createTaskEvent(
                taskExecution, TaskEventType.STARTED);

        // Assert
        assertNotNull(taskEvent);
        assertEquals(TaskEventType.STARTED, taskEvent.getEventType());
        assertEquals(taskExecution.getId(), taskEvent.getTaskExecutionId());
        assertEquals(taskExecution.getTaskDefinition().getId(), taskEvent.getTaskDefinitionId());
        assertEquals(taskExecution.getTaskDefinition().getName(), taskEvent.getTaskName());
        assertEquals(taskExecution.getTaskDefinition().getType(), taskEvent.getTaskType());
        assertEquals(taskExecution.getWorkflowExecutionId(), taskEvent.getWorkflowExecutionId());
    }

    @Test
    void createUserReviewEvent_shouldPopulateAllFields() {
        // Act
        UserReviewEvent reviewEvent = eventPublisherService.createUserReviewEvent(
                workflowExecution, reviewPoint, UserReviewEventType.REQUESTED);

        // Assert
        assertNotNull(reviewEvent);
        assertEquals(UserReviewEventType.REQUESTED, reviewEvent.getEventType());
        assertEquals(workflowExecution.getId(), reviewEvent.getWorkflowExecutionId());
        assertEquals(workflowExecution.getWorkflowDefinition().getId(), reviewEvent.getWorkflowDefinitionId());
        assertEquals(workflowExecution.getWorkflowDefinition().getName(), reviewEvent.getWorkflowName());
        assertEquals(workflowExecution.getCorrelationId(), reviewEvent.getCorrelationId());
        assertEquals(reviewPoint.getId(), reviewEvent.getReviewPointId());
        assertEquals(reviewPoint.getTaskExecutionId(), reviewEvent.getTaskExecutionId());
    }

    @Test
    void publishEvent_shouldDelegateToEventPublisher() {
        // Arrange
        WorkflowEvent workflowEvent = new WorkflowEvent(eventPublisherService);
        workflowEvent.setEventType(WorkflowEventType.STARTED);

        // Act
        eventPublisherService.publishEvent(workflowEvent);

        // Assert
        verify(eventPublisher).publishEvent(workflowEvent);
    }

    @Test
    void createTaskEventById_shouldCreateTaskEvent() {
        // Act
        TaskEvent taskEvent = eventPublisherService.createTaskEventById(
                taskExecution.getId(), TaskEventType.STARTED);

        // Assert
        assertNotNull(taskEvent);
        assertEquals(TaskEventType.STARTED, taskEvent.getEventType());
        assertEquals(taskExecution.getId(), taskEvent.getTaskExecutionId());
    }

    @Test
    void createWorkflowEventById_shouldCreateWorkflowEvent() {
        // Act
        WorkflowEvent workflowEvent = eventPublisherService.createWorkflowEventById(
                workflowExecution.getId(), WorkflowEventType.STARTED);

        // Assert
        assertNotNull(workflowEvent);
        assertEquals(WorkflowEventType.STARTED, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
    }

    @Test
    void createTaskReplayEvent_shouldCreateEventWithReplayStrategy() {
        // Arrange
        UserReviewPoint.ReplayStrategy replayStrategy = UserReviewPoint.ReplayStrategy.SELECTED_TASKS;

        // Act
        TaskEvent taskEvent = eventPublisherService.createTaskReplayEvent(
                taskExecution, replayStrategy);

        // Assert
        assertNotNull(taskEvent);
        assertEquals(TaskEventType.REPLAY, taskEvent.getEventType());
        assertEquals(taskExecution.getId(), taskEvent.getTaskExecutionId());
        assertEquals("SELECTED_TASKS", taskEvent.getProperties().get("replayStrategy"));
    }

    @Test
    void createTaskOverrideEvent_shouldCreateEventWithAllOverrideProperties() {
        // Arrange
        TaskStatus originalStatus = TaskStatus.RUNNING;
        TaskStatus newStatus = TaskStatus.COMPLETED;
        String overriddenBy = "test-user";

        // Act
        TaskEvent taskEvent = eventPublisherService.createTaskOverrideEvent(
                taskExecution, originalStatus, newStatus, overriddenBy);

        // Assert
        assertNotNull(taskEvent);
        assertEquals(TaskEventType.OVERRIDE, taskEvent.getEventType());
        assertEquals(taskExecution.getId(), taskEvent.getTaskExecutionId());
        assertEquals("RUNNING", taskEvent.getProperties().get("originalStatus"));
        assertEquals("COMPLETED", taskEvent.getProperties().get("newStatus"));
        assertEquals("test-user", taskEvent.getProperties().get("overriddenBy"));
    }

    @Test
    void createWorkflowReplayEvent_shouldCreateEventWithTaskIdProperties() {
        // Arrange
        List<Long> replayTaskIds = List.of(1L, 2L, 3L);

        // Act
        WorkflowEvent workflowEvent = eventPublisherService.createWorkflowReplayEvent(
                workflowExecution, replayTaskIds);

        // Assert
        assertNotNull(workflowEvent);
        assertEquals(WorkflowEventType.REPLAY, workflowEvent.getEventType());
        assertEquals(workflowExecution.getId(), workflowEvent.getWorkflowExecutionId());
        assertEquals("3", workflowEvent.getProperties().get("replayTaskCount"));
        assertEquals(replayTaskIds.toString(), workflowEvent.getProperties().get("replayTaskIds"));
    }
}