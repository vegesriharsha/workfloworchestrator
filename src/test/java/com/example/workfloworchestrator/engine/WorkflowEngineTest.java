package com.example.workfloworchestrator.engine;

import com.example.workfloworchestrator.engine.strategy.ExecutionStrategy;
import com.example.workfloworchestrator.event.TaskEvent;
import com.example.workfloworchestrator.event.TaskEventType;
import com.example.workfloworchestrator.event.WorkflowEvent;
import com.example.workfloworchestrator.event.WorkflowEventType;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.example.workfloworchestrator.util.WorkflowNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private WorkflowExecutionService workflowExecutionService;
    
    @Mock
    private TaskExecutionService taskExecutionService;
    
    @Mock
    private EventPublisherService eventPublisherService;
    
    @Mock
    private ExecutionStrategy sequentialStrategy;
    
    @Mock
    private ExecutionStrategy parallelStrategy;
    
    @Mock
    private ExecutionStrategy conditionalStrategy;
    
    @Mock
    private ExecutionStrategy graphStrategy;
    
    @Mock
    private ExecutionStrategy hybridStrategy;
    
    @Mock
    private WorkflowNotificationService notificationService;
    
    @Captor
    private ArgumentCaptor<WorkflowStatus> statusCaptor;
    
    private Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> executionStrategies;
    
    private WorkflowEngine workflowEngine;
    
    private WorkflowExecution workflowExecution;
    private TaskExecution taskExecution;
    private WorkflowDefinition workflowDefinition;
    private TaskDefinition taskDefinition;
    
    @BeforeEach
    void setUp() {
        executionStrategies = new HashMap<>();
        executionStrategies.put(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL, sequentialStrategy);
        executionStrategies.put(WorkflowDefinition.ExecutionStrategyType.PARALLEL, parallelStrategy);
        executionStrategies.put(WorkflowDefinition.ExecutionStrategyType.CONDITIONAL, conditionalStrategy);
        executionStrategies.put(WorkflowDefinition.ExecutionStrategyType.GRAPH, graphStrategy);
        executionStrategies.put(WorkflowDefinition.ExecutionStrategyType.HYBRID, hybridStrategy);
        
        workflowEngine = new WorkflowEngine(
                workflowExecutionService, 
                taskExecutionService, 
                eventPublisherService, 
                executionStrategies, 
                notificationService
        );
        
        // Setup WorkflowDefinition
        taskDefinition = TaskDefinition.builder()
                .id(1L)
                .name("TaskName")
                .description("Task Description")
                .type("REST")
                .executionOrder(1)
                .build();
        
        workflowDefinition = WorkflowDefinition.builder()
                .id(1L)
                .name("WorkflowName")
                .description("Workflow Description")
                .version("1.0")
                .strategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)
                .tasks(List.of(taskDefinition))
                .build();
        
        // Setup WorkflowExecution
        workflowExecution = WorkflowExecution.builder()
                .id(1L)
                .workflowDefinition(workflowDefinition)
                .correlationId("correlation-id-123")
                .status(WorkflowStatus.CREATED)
                .startedAt(LocalDateTime.now())
                .currentTaskIndex(0)
                .reviewPoints(new ArrayList<>())
                .build();
        
        // Setup TaskExecution
        taskExecution = TaskExecution.builder()
                .id(1L)
                .taskDefinition(taskDefinition)
                .workflowExecutionId(workflowExecution.getId())
                .status(TaskStatus.PENDING)
                .inputs(new HashMap<>())
                .outputs(new HashMap<>())
                .retryCount(0)
                .build();
    }

    @Test
    void executeWorkflow_withCreatedStatus_shouldUpdateToRunning() {
        // Arrange
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(sequentialStrategy.execute(workflowExecution))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Act
        workflowEngine.executeWorkflow(workflowExecution.getId());
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.RUNNING));
        verify(eventPublisherService).publishWorkflowStartedEvent(workflowExecution);
        verify(sequentialStrategy).execute(workflowExecution);
        verify(workflowExecutionService, timeout(1000)).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.COMPLETED));
        verify(eventPublisherService, timeout(1000)).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void executeWorkflow_withRunningStatus_shouldSkipStatusUpdate() {
        // Arrange
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(sequentialStrategy.execute(workflowExecution))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Act
        workflowEngine.executeWorkflow(workflowExecution.getId());
        
        // Assert
        verify(workflowExecutionService, never()).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.RUNNING));
        verify(eventPublisherService, never()).publishWorkflowStartedEvent(workflowExecution);
        verify(sequentialStrategy).execute(workflowExecution);
        verify(workflowExecutionService, timeout(1000)).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.COMPLETED));
        verify(eventPublisherService, timeout(1000)).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void executeWorkflow_whenStrategyReturnsFailedStatus_shouldPublishFailedEvent() {
        // Arrange
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(sequentialStrategy.execute(workflowExecution))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.FAILED));
        
        // Act
        workflowEngine.executeWorkflow(workflowExecution.getId());
        
        // Assert
        verify(workflowExecutionService, timeout(1000)).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService, timeout(1000)).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void executeWorkflow_withInvalidStatus_shouldNotExecute() {
        // Arrange
        workflowExecution.setStatus(WorkflowStatus.COMPLETED);
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        
        // Act
        workflowEngine.executeWorkflow(workflowExecution.getId());
        
        // Assert
        verify(sequentialStrategy, never()).execute(any(WorkflowExecution.class));
    }
    
    @Test
    void executeWorkflow_withException_shouldUpdateToFailedStatus() {
        // Arrange
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(sequentialStrategy.execute(workflowExecution))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Act
        workflowEngine.executeWorkflow(workflowExecution.getId());
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void executeWorkflow_withNonExistentStrategy_shouldFallbackToSequential() {
        // Arrange
        workflowDefinition.setStrategyType(null); // Invalid strategy
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(sequentialStrategy.execute(workflowExecution))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Act
        workflowEngine.executeWorkflow(workflowExecution.getId());
        
        // Assert
        verify(sequentialStrategy).execute(workflowExecution);
    }
    
    @Test
    void executeWorkflow_withNoStrategyAvailable_shouldThrowException() {
        // Arrange
        executionStrategies.clear(); // Remove all strategies
        workflowEngine = new WorkflowEngine(
                workflowExecutionService, 
                taskExecutionService, 
                eventPublisherService, 
                executionStrategies, 
                notificationService
        );
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        
        // Act & Assert
        workflowEngine.executeWorkflow(workflowExecution.getId());
        
        // Verify failure is handled
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }

    @Test
    void restartTask_shouldResetTaskAndContinueWorkflow() {
        // Arrange
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId()))
                .thenReturn(taskExecution);
        when(taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId()))
                .thenReturn(List.of(taskExecution));
        
        // Act
        workflowEngine.restartTask(workflowExecution.getId(), taskExecution.getId());
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.RUNNING));
        
        verify(taskExecutionService).saveTaskExecution(argThat(te -> 
                te.getStatus() == TaskStatus.PENDING &&
                te.getStartedAt() == null &&
                te.getCompletedAt() == null &&
                te.getErrorMessage() == null &&
                te.getRetryCount() == 0 &&
                te.getOutputs().isEmpty()
        ));
        
        verify(workflowExecutionService).save(argThat(we -> 
                we.getCurrentTaskIndex() == 0
        ));
        
        verify(workflowEngine).executeWorkflow(workflowExecution.getId());
    }
    
    @Test
    void restartTask_withException_shouldUpdateToFailedStatus() {
        // Arrange
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId()))
                .thenReturn(taskExecution);
        doThrow(new RuntimeException("Test exception"))
                .when(taskExecutionService).saveTaskExecution(any(TaskExecution.class));
        
        // Act
        workflowEngine.restartTask(workflowExecution.getId(), taskExecution.getId());
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void restartTask_taskNotFound_shouldHandleNegativeIndex() {
        // Arrange
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId()))
                .thenReturn(taskExecution);
        when(taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId()))
                .thenReturn(List.of()); // Empty list, task not found
        
        // Act
        workflowEngine.restartTask(workflowExecution.getId(), taskExecution.getId());
        
        // Assert
        verify(workflowExecutionService, never()).save(any(WorkflowExecution.class));
        verify(workflowEngine).executeWorkflow(workflowExecution.getId());
    }
    
    @Test
    void executeTaskSubset_shouldExecuteStrategyWithTaskIds() {
        // Arrange
        List<Long> taskIds = List.of(1L, 2L, 3L);
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(sequentialStrategy.executeSubset(workflowExecution, taskIds))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Act
        workflowEngine.executeTaskSubset(workflowExecution.getId(), taskIds);
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.RUNNING));
        verify(sequentialStrategy).executeSubset(workflowExecution, taskIds);
        verify(workflowExecutionService, timeout(1000)).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.COMPLETED));
        verify(eventPublisherService, timeout(1000)).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void executeTaskSubset_withException_shouldUpdateToFailedStatus() {
        // Arrange
        List<Long> taskIds = List.of(1L, 2L, 3L);
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(sequentialStrategy.executeSubset(workflowExecution, taskIds))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Act
        workflowEngine.executeTaskSubset(workflowExecution.getId(), taskIds);
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void replayTasks_shouldResetTasksAndExecuteSubset() {
        // Arrange
        List<Long> taskIds = List.of(1L, 2L);
        boolean preserveOutputs = false;
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(1L))
                .thenReturn(taskExecution);
        when(taskExecutionService.getTaskExecution(2L))
                .thenReturn(taskExecution);
        when(taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId()))
                .thenReturn(List.of(taskExecution, taskExecution));
        when(eventPublisherService.createWorkflowEvent(eq(workflowExecution), eq(WorkflowEventType.REPLAY)))
                .thenReturn(new WorkflowEvent(eventPublisherService));
        
        // Act
        workflowEngine.replayTasks(workflowExecution.getId(), taskIds, preserveOutputs);
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.RUNNING));
        
        // Should reset both tasks
        verify(taskExecutionService, times(2)).saveTaskExecution(argThat(te -> 
                te.getStatus() == TaskStatus.PENDING &&
                te.getStartedAt() == null &&
                te.getCompletedAt() == null &&
                te.getErrorMessage() == null &&
                te.getRetryCount() == 0 &&
                te.getOutputs().isEmpty()
        ));
        
        // Should update workflow with minimum task index (0)
        verify(workflowExecutionService).save(argThat(we -> 
                we.getCurrentTaskIndex() == 0
        ));
        
        // Should publish replay event
        verify(eventPublisherService).publishEvent(any(WorkflowEvent.class));
        
        // Should execute subset
        verify(workflowEngine).executeTaskSubset(eq(workflowExecution.getId()), eq(taskIds));
    }
    
    @Test
    void replayTasks_withPreserveOutputs_shouldKeepTaskOutputs() {
        // Arrange
        List<Long> taskIds = List.of(1L);
        boolean preserveOutputs = true;
        
        Map<String, String> existingOutputs = new HashMap<>();
        existingOutputs.put("key1", "value1");
        taskExecution.setOutputs(existingOutputs);
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(1L))
                .thenReturn(taskExecution);
        when(taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId()))
                .thenReturn(List.of(taskExecution));
        when(eventPublisherService.createWorkflowEvent(eq(workflowExecution), eq(WorkflowEventType.REPLAY)))
                .thenReturn(new WorkflowEvent(eventPublisherService));
        
        // Act
        workflowEngine.replayTasks(workflowExecution.getId(), taskIds, preserveOutputs);
        
        // Assert
        verify(taskExecutionService).saveTaskExecution(argThat(te -> 
                te.getOutputs().equals(existingOutputs)
        ));
    }
    
    @Test
    void replayTasks_withException_shouldUpdateToFailedStatus() {
        // Arrange
        List<Long> taskIds = List.of(1L);
        boolean preserveOutputs = false;
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(1L))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Act
        workflowEngine.replayTasks(workflowExecution.getId(), taskIds, preserveOutputs);
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void overrideTaskStatus_withCompletedStatus_shouldSetCompletedStatus() {
        // Arrange
        TaskStatus newStatus = TaskStatus.COMPLETED;
        String overrideReason = "Manual approval";
        String overriddenBy = "admin";
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId()))
                .thenReturn(taskExecution);
        when(eventPublisherService.createTaskEvent(eq(taskExecution), eq(TaskEventType.OVERRIDE)))
                .thenReturn(new TaskEvent(eventPublisherService));
        
        // Act
        workflowEngine.overrideTaskStatus(workflowExecution.getId(), taskExecution.getId(), 
                newStatus, overrideReason, overriddenBy);
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.RUNNING));
        
        verify(taskExecutionService).saveTaskExecution(argThat(te -> 
                te.getStatus() == TaskStatus.COMPLETED &&
                te.getCompletedAt() != null &&
                te.getOutputs().get("overridden").equals("true") &&
                te.getOutputs().get("overriddenBy").equals(overriddenBy) &&
                te.getOutputs().get("overrideReason").equals(overrideReason) &&
                te.getOutputs().get("originalStatus").equals(taskExecution.getStatus().toString())
        ));
        
        verify(eventPublisherService).publishEvent(any(TaskEvent.class));
        verify(workflowEngine).executeWorkflow(workflowExecution.getId());
    }
    
    @Test
    void overrideTaskStatus_withFailedStatus_shouldSetFailedStatusWithErrorMessage() {
        // Arrange
        TaskStatus newStatus = TaskStatus.FAILED;
        String overrideReason = "Manual rejection";
        String overriddenBy = "admin";
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId()))
                .thenReturn(taskExecution);
        when(eventPublisherService.createTaskEvent(eq(taskExecution), eq(TaskEventType.OVERRIDE)))
                .thenReturn(new TaskEvent(eventPublisherService));
        
        // Act
        workflowEngine.overrideTaskStatus(workflowExecution.getId(), taskExecution.getId(), 
                newStatus, overrideReason, overriddenBy);
        
        // Assert
        verify(taskExecutionService).saveTaskExecution(argThat(te -> 
                te.getStatus() == TaskStatus.FAILED &&
                te.getErrorMessage().contains(overriddenBy) &&
                te.getErrorMessage().contains(overrideReason)
        ));
    }
    
    @Test
    void overrideTaskStatus_withSkippedStatus_shouldSetSkippedStatus() {
        // Arrange
        TaskStatus newStatus = TaskStatus.SKIPPED;
        String overrideReason = "Task not needed";
        String overriddenBy = "admin";
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId()))
                .thenReturn(taskExecution);
        when(eventPublisherService.createTaskEvent(eq(taskExecution), eq(TaskEventType.OVERRIDE)))
                .thenReturn(new TaskEvent(eventPublisherService));
        
        // Act
        workflowEngine.overrideTaskStatus(workflowExecution.getId(), taskExecution.getId(), 
                newStatus, overrideReason, overriddenBy);
        
        // Assert
        verify(taskExecutionService).saveTaskExecution(argThat(te -> 
                te.getStatus() == TaskStatus.SKIPPED
        ));
    }
    
    @Test
    void overrideTaskStatus_withInvalidStatus_shouldThrowException() {
        // Arrange
        TaskStatus newStatus = TaskStatus.RUNNING; // Invalid for override
        String overrideReason = "Cannot set to running";
        String overriddenBy = "admin";
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId()))
                .thenReturn(taskExecution);
        
        // Act
        workflowEngine.overrideTaskStatus(workflowExecution.getId(), taskExecution.getId(), 
                newStatus, overrideReason, overriddenBy);
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void overrideTaskStatus_withException_shouldUpdateToFailedStatus() {
        // Arrange
        TaskStatus newStatus = TaskStatus.COMPLETED;
        String overrideReason = "Manual approval";
        String overriddenBy = "admin";
        
        when(workflowExecutionService.getWorkflowExecution(workflowExecution.getId()))
                .thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId()))
                .thenReturn(taskExecution);
        doThrow(new RuntimeException("Test exception"))
                .when(taskExecutionService).saveTaskExecution(any(TaskExecution.class));
        
        // Act
        workflowEngine.overrideTaskStatus(workflowExecution.getId(), taskExecution.getId(), 
                newStatus, overrideReason, overriddenBy);
        
        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void createEnhancedUserReviewPoint_shouldCreateReviewPointAndUpdateStatus() {
        // Arrange
        Long taskExecutionId = 1L;
        
        when(taskExecutionService.getTaskExecution(taskExecutionId))
                .thenReturn(taskExecution);
        when(workflowExecutionService.getWorkflowExecution(taskExecution.getWorkflowExecutionId()))
                .thenReturn(workflowExecution);
        
        // Act
        UserReviewPoint result = workflowEngine.createEnhancedUserReviewPoint(taskExecutionId);
        
        // Assert
        assertNotNull(result);
        assertEquals(taskExecutionId, result.getTaskExecutionId());
        assertNotNull(result.getCreatedAt());
        
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                eq(workflowExecution.getId()), eq(WorkflowStatus.AWAITING_USER_REVIEW));
        verify(workflowExecutionService).save(argThat(we -> 
                we.getReviewPoints().size() == 1 &&
                we.getReviewPoints().get(0) == result
        ));
        verify(eventPublisherService).publishUserReviewRequestedEvent(workflowExecution, result);
        verify(notificationService).notifyReviewersForReviewPoint(workflowExecution, result);
    }
}