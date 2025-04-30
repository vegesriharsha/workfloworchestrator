package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.WorkflowExecutor;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkflowExecutionServiceTest {

    @Mock
    private WorkflowService workflowService;

    @Mock
    private WorkflowExecutionRepository workflowExecutionRepository;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private WorkflowExecutor workflowExecutor;

    @InjectMocks
    private WorkflowExecutionService workflowExecutionService;

    @Captor
    private ArgumentCaptor<WorkflowExecution> workflowExecutionCaptor;

    private WorkflowDefinition workflowDefinition;
    private WorkflowExecution workflowExecution;

    @BeforeEach
    void setUp() {
        // Set up WorkflowExecutor in the service
        ReflectionTestUtils.setField(workflowExecutionService, "workflowExecutor", workflowExecutor);
        
        // Initialize test data
        workflowDefinition = WorkflowDefinition.builder()
                .id(1L)
                .name("TestWorkflow")
                .description("Test Workflow Description")
                .version("1.0")
                .createdAt(LocalDateTime.now())
                .strategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)
                .build();

        workflowExecution = WorkflowExecution.builder()
                .id(1L)
                .workflowDefinition(workflowDefinition)
                .correlationId("test-correlation-id")
                .status(WorkflowStatus.CREATED)
                .startedAt(LocalDateTime.now())
                .currentTaskIndex(0)
                .retryCount(0)
                .variables(new HashMap<>())
                .build();
    }

    @Test
    void startWorkflow_shouldCreateAndExecuteWorkflow() {
        // Arrange
        String workflowName = "TestWorkflow";
        String version = "1.0";
        Map<String, String> variables = Map.of("key", "value");
        
        when(workflowService.getWorkflowDefinition(workflowName, version))
                .thenReturn(Optional.of(workflowDefinition));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.startWorkflow(workflowName, version, variables);
        
        // Assert
        assertNotNull(result);
        assertEquals(workflowExecution, result);
        
        verify(workflowService).getWorkflowDefinition(workflowName, version);
        verify(workflowExecutionRepository).save(any(WorkflowExecution.class));
        verify(workflowExecutor).executeWorkflow(workflowExecution.getId());
    }
    
    @Test
    void startWorkflow_withNoVersion_shouldUseLatestVersion() {
        // Arrange
        String workflowName = "TestWorkflow";
        String version = null;
        Map<String, String> variables = Map.of("key", "value");
        
        when(workflowService.getLatestWorkflowDefinition(workflowName))
                .thenReturn(Optional.of(workflowDefinition));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.startWorkflow(workflowName, version, variables);
        
        // Assert
        assertNotNull(result);
        assertEquals(workflowExecution, result);
        
        verify(workflowService).getLatestWorkflowDefinition(workflowName);
        verify(workflowExecutionRepository).save(any(WorkflowExecution.class));
        verify(workflowExecutor).executeWorkflow(workflowExecution.getId());
    }
    
    @Test
    void startWorkflow_withNonExistentWorkflow_shouldThrowException() {
        // Arrange
        String workflowName = "NonExistentWorkflow";
        String version = "1.0";
        Map<String, String> variables = Map.of("key", "value");
        
        when(workflowService.getWorkflowDefinition(workflowName, version))
                .thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(WorkflowException.class, () -> 
                workflowExecutionService.startWorkflow(workflowName, version, variables));
        
        verify(workflowService).getWorkflowDefinition(workflowName, version);
        verify(workflowExecutionRepository, never()).save(any(WorkflowExecution.class));
        verify(workflowExecutor, never()).executeWorkflow(anyLong());
    }
    
    @Test
    void createWorkflowExecution_shouldCreateWithCorrectDefaults() {
        // Arrange
        Map<String, String> variables = Map.of("key", "value");
        
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        WorkflowExecution result = workflowExecutionService.createWorkflowExecution(workflowDefinition, variables);
        
        // Assert
        assertNotNull(result);
        assertEquals(workflowDefinition, result.getWorkflowDefinition());
        assertEquals(variables, result.getVariables());
        assertEquals(WorkflowStatus.CREATED, result.getStatus());
        assertEquals(0, result.getCurrentTaskIndex());
        assertEquals(0, result.getRetryCount());
        assertNotNull(result.getStartedAt());
        assertNotNull(result.getCorrelationId());
        
        verify(workflowExecutionRepository).save(any(WorkflowExecution.class));
    }
    
    @Test
    void createWorkflowExecution_withNullVariables_shouldCreateWithEmptyVariables() {
        // Arrange
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        WorkflowExecution result = workflowExecutionService.createWorkflowExecution(workflowDefinition, null);
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.getVariables());
        assertTrue(result.getVariables().isEmpty());
        
        verify(workflowExecutionRepository).save(any(WorkflowExecution.class));
    }
    
    @Test
    void getWorkflowExecution_shouldReturnWorkflowExecution() {
        // Arrange
        Long id = 1L;
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.getWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(workflowExecution, result);
        
        verify(workflowExecutionRepository).findById(id);
    }
    
    @Test
    void getWorkflowExecution_withNonExistentId_shouldThrowException() {
        // Arrange
        Long id = 999L;
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(WorkflowException.class, () -> 
                workflowExecutionService.getWorkflowExecution(id));
        
        verify(workflowExecutionRepository).findById(id);
    }
    
    @Test
    void getWorkflowExecutionByCorrelationId_shouldReturnWorkflowExecution() {
        // Arrange
        String correlationId = "test-correlation-id";
        
        when(workflowExecutionRepository.findByCorrelationId(correlationId))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.getWorkflowExecutionByCorrelationId(correlationId);
        
        // Assert
        assertNotNull(result);
        assertEquals(workflowExecution, result);
        
        verify(workflowExecutionRepository).findByCorrelationId(correlationId);
    }
    
    @Test
    void getWorkflowExecutionByCorrelationId_withNonExistentId_shouldThrowException() {
        // Arrange
        String correlationId = "non-existent-id";
        
        when(workflowExecutionRepository.findByCorrelationId(correlationId))
                .thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(WorkflowException.class, () -> 
                workflowExecutionService.getWorkflowExecutionByCorrelationId(correlationId));
        
        verify(workflowExecutionRepository).findByCorrelationId(correlationId);
    }
    
    @Test
    void updateWorkflowExecutionStatus_shouldUpdateStatusAndSave() {
        // Arrange
        Long id = 1L;
        WorkflowStatus newStatus = WorkflowStatus.RUNNING;
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.updateWorkflowExecutionStatus(id, newStatus);
        
        // Assert
        assertNotNull(result);
        assertEquals(newStatus, result.getStatus());
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(eventPublisherService).publishWorkflowStatusChangedEvent(workflowExecution);
    }
    
    @Test
    void updateWorkflowExecutionStatus_toCompletedStatus_shouldSetCompletedAt() {
        // Arrange
        Long id = 1L;
        WorkflowStatus newStatus = WorkflowStatus.COMPLETED;
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.updateWorkflowExecutionStatus(id, newStatus);
        
        // Assert
        assertNotNull(result);
        assertEquals(newStatus, result.getStatus());
        assertNotNull(result.getCompletedAt());
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(eventPublisherService).publishWorkflowStatusChangedEvent(workflowExecution);
    }
    
    @Test
    void updateWorkflowExecutionStatus_toFailedStatus_shouldSetCompletedAt() {
        // Arrange
        Long id = 1L;
        WorkflowStatus newStatus = WorkflowStatus.FAILED;
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.updateWorkflowExecutionStatus(id, newStatus);
        
        // Assert
        assertNotNull(result);
        assertEquals(newStatus, result.getStatus());
        assertNotNull(result.getCompletedAt());
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(eventPublisherService).publishWorkflowStatusChangedEvent(workflowExecution);
    }
    
    @Test
    void pauseWorkflowExecution_whenRunning_shouldPauseAndSave() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.pauseWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.PAUSED, result.getStatus());
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(eventPublisherService).publishWorkflowPausedEvent(workflowExecution);
    }
    
    @Test
    void pauseWorkflowExecution_whenNotRunning_shouldNotPauseAndReturn() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.FAILED);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.pauseWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.FAILED, result.getStatus()); // Status remains FAILED
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository, never()).save(any(WorkflowExecution.class));
        verify(eventPublisherService, never()).publishWorkflowPausedEvent(any(WorkflowExecution.class));
    }
    
    @Test
    void resumeWorkflowExecution_whenPaused_shouldResumeAndExecute() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.PAUSED);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.resumeWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.RUNNING, result.getStatus());
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(workflowExecutor).executeWorkflow(id);
        verify(eventPublisherService).publishWorkflowResumedEvent(workflowExecution);
    }
    
    @Test
    void resumeWorkflowExecution_whenNotPaused_shouldNotResumeAndReturn() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.resumeWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.RUNNING, result.getStatus()); // Status remains RUNNING
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository, never()).save(any(WorkflowExecution.class));
        verify(workflowExecutor, never()).executeWorkflow(anyLong());
        verify(eventPublisherService, never()).publishWorkflowResumedEvent(any(WorkflowExecution.class));
    }
    
    @Test
    void cancelWorkflowExecution_whenRunning_shouldCancelAndSave() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.cancelWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.CANCELLED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(eventPublisherService).publishWorkflowCancelledEvent(workflowExecution);
    }
    
    @Test
    void cancelWorkflowExecution_whenCompleted_shouldNotCancelAndReturn() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.COMPLETED);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.cancelWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.COMPLETED, result.getStatus()); // Status remains COMPLETED
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository, never()).save(any(WorkflowExecution.class));
        verify(eventPublisherService, never()).publishWorkflowCancelledEvent(any(WorkflowExecution.class));
    }
    
    @Test
    void retryWorkflowExecution_whenFailed_shouldRetryAndExecute() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.FAILED);
        workflowExecution.setRetryCount(1);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.retryWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.RUNNING, result.getStatus());
        assertEquals(2, result.getRetryCount()); // Incremented from 1 to 2
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(workflowExecutor).executeWorkflow(id);
        verify(eventPublisherService).publishWorkflowRetryEvent(workflowExecution);
    }
    
    @Test
    void retryWorkflowExecution_whenNotFailed_shouldNotRetryAndReturn() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.retryWorkflowExecution(id);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.RUNNING, result.getStatus()); // Status remains RUNNING
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository, never()).save(any(WorkflowExecution.class));
        verify(workflowExecutor, never()).executeWorkflow(anyLong());
        verify(eventPublisherService, never()).publishWorkflowRetryEvent(any(WorkflowExecution.class));
    }
    
    @Test
    void getWorkflowExecutionsByStatus_shouldReturnMatchingExecutions() {
        // Arrange
        WorkflowStatus status = WorkflowStatus.RUNNING;
        List<WorkflowExecution> expectedExecutions = List.of(workflowExecution);
        
        when(workflowExecutionRepository.findByStatus(status))
                .thenReturn(expectedExecutions);
        
        // Act
        List<WorkflowExecution> result = workflowExecutionService.getWorkflowExecutionsByStatus(status);
        
        // Assert
        assertNotNull(result);
        assertEquals(expectedExecutions, result);
        
        verify(workflowExecutionRepository).findByStatus(status);
    }
    
    @Test
    void getStuckWorkflowExecutions_shouldReturnStuckExecutions() {
        // Arrange
        LocalDateTime before = LocalDateTime.now().minusHours(2);
        List<WorkflowExecution> expectedExecutions = List.of(workflowExecution);
        
        when(workflowExecutionRepository.findStuckExecutions(WorkflowStatus.RUNNING, before))
                .thenReturn(expectedExecutions);
        
        // Act
        List<WorkflowExecution> result = workflowExecutionService.getStuckWorkflowExecutions(before);
        
        // Assert
        assertNotNull(result);
        assertEquals(expectedExecutions, result);
        
        verify(workflowExecutionRepository).findStuckExecutions(WorkflowStatus.RUNNING, before);
    }
    
    @Test
    void save_shouldSaveWorkflowExecution() {
        // Arrange
        when(workflowExecutionRepository.save(workflowExecution))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.save(workflowExecution);
        
        // Assert
        assertNotNull(result);
        assertEquals(workflowExecution, result);
        
        verify(workflowExecutionRepository).save(workflowExecution);
    }
    
    @Test
    void getAllWorkflowExecutions_shouldReturnAllExecutions() {
        // Arrange
        List<WorkflowExecution> expectedExecutions = List.of(workflowExecution);
        
        when(workflowExecutionRepository.findAll())
                .thenReturn(expectedExecutions);
        
        // Act
        List<WorkflowExecution> result = workflowExecutionService.getAllWorkflowExecutions();
        
        // Assert
        assertNotNull(result);
        assertEquals(expectedExecutions, result);
        
        verify(workflowExecutionRepository).findAll();
    }
    
    @Test
    void findWorkflowExecutionByCorrelationId_shouldReturnOptionalExecution() {
        // Arrange
        String correlationId = "test-correlation-id";
        
        when(workflowExecutionRepository.findByCorrelationId(correlationId))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        Optional<WorkflowExecution> result = workflowExecutionService.findWorkflowExecutionByCorrelationId(correlationId);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(workflowExecution, result.get());
        
        verify(workflowExecutionRepository).findByCorrelationId(correlationId);
    }
    
    @Test
    void retryWorkflowExecutionSubset_whenFailed_shouldRetrySubsetAndExecute() {
        // Arrange
        Long id = 1L;
        List<Long> taskIds = List.of(10L, 20L);
        workflowExecution.setStatus(WorkflowStatus.FAILED);
        workflowExecution.setRetryCount(0);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.retryWorkflowExecutionSubset(id, taskIds);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.RUNNING, result.getStatus());
        assertEquals(1, result.getRetryCount()); // Incremented from 0 to 1
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(workflowExecutor).executeTaskSubset(id, taskIds);
        verify(eventPublisherService).publishWorkflowRetryEvent(workflowExecution);
    }
    
    @Test
    void retryWorkflowExecutionSubset_whenPaused_shouldRetrySubsetAndExecute() {
        // Arrange
        Long id = 1L;
        List<Long> taskIds = List.of(10L, 20L);
        workflowExecution.setStatus(WorkflowStatus.PAUSED);
        workflowExecution.setRetryCount(0);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(workflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.retryWorkflowExecutionSubset(id, taskIds);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.RUNNING, result.getStatus());
        assertEquals(1, result.getRetryCount()); // Incremented from 0 to 1
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecution);
        verify(workflowExecutor).executeTaskSubset(id, taskIds);
        verify(eventPublisherService).publishWorkflowRetryEvent(workflowExecution);
    }
    
    @Test
    void retryWorkflowExecutionSubset_whenNotFailedOrPaused_shouldNotRetry() {
        // Arrange
        Long id = 1L;
        List<Long> taskIds = List.of(10L, 20L);
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.retryWorkflowExecutionSubset(id, taskIds);
        
        // Assert
        assertNotNull(result);
        assertEquals(WorkflowStatus.RUNNING, result.getStatus()); // Status remains RUNNING
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository, never()).save(any(WorkflowExecution.class));
        verify(workflowExecutor, never()).executeTaskSubset(anyLong(), anyList());
        verify(eventPublisherService, never()).publishWorkflowRetryEvent(any(WorkflowExecution.class));
    }
    
    @Test
    void findCompletedWorkflowsOlderThan_shouldFilterCompletedWorkflows() {
        // Arrange
        LocalDateTime before = LocalDateTime.now().minusDays(7);
        
        // Create multiple workflow executions with different completion dates
        WorkflowExecution oldExecution = WorkflowExecution.builder()
                .id(1L)
                .status(WorkflowStatus.COMPLETED)
                .completedAt(before.minusDays(1)) // Older than threshold
                .build();
        
        WorkflowExecution newExecution = WorkflowExecution.builder()
                .id(2L)
                .status(WorkflowStatus.COMPLETED)
                .completedAt(before.plusDays(1)) // Newer than threshold
                .build();
        
        WorkflowExecution failedExecution = WorkflowExecution.builder()
                .id(3L)
                .status(WorkflowStatus.FAILED)
                .completedAt(before.minusDays(2)) // Failed and older than threshold
                .build();
        
        List<WorkflowExecution> allCompletedExecutions = List.of(
                oldExecution, newExecution, failedExecution
        );
        
        List<WorkflowStatus> terminalStatuses = List.of(
                WorkflowStatus.COMPLETED, WorkflowStatus.FAILED, WorkflowStatus.CANCELLED
        );
        
        when(workflowExecutionRepository.findByStatusIn(terminalStatuses))
                .thenReturn(allCompletedExecutions);
        
        // Act
        List<WorkflowExecution> result = workflowExecutionService.findCompletedWorkflowsOlderThan(before);
        
        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(oldExecution));
        assertTrue(result.contains(failedExecution));
        assertFalse(result.contains(newExecution));
        
        verify(workflowExecutionRepository).findByStatusIn(terminalStatuses);
    }
    
    @Test
    void findPausedWorkflowsOlderThan_shouldFilterPausedWorkflows() {
        // Arrange
        LocalDateTime before = LocalDateTime.now().minusDays(1);
        
        // Create multiple workflow executions with different start dates
        WorkflowExecution oldExecution = WorkflowExecution.builder()
                .id(1L)
                .status(WorkflowStatus.PAUSED)
                .startedAt(before.minusDays(1)) // Older than threshold
                .build();
        
        WorkflowExecution newExecution = WorkflowExecution.builder()
                .id(2L)
                .status(WorkflowStatus.PAUSED)
                .startedAt(before.plusDays(1)) // Newer than threshold
                .build();
        
        List<WorkflowExecution> allPausedExecutions = List.of(
                oldExecution, newExecution
        );
        
        when(workflowExecutionRepository.findByStatus(WorkflowStatus.PAUSED))
                .thenReturn(allPausedExecutions);
        
        // Act
        List<WorkflowExecution> result = workflowExecutionService.findPausedWorkflowsOlderThan(before);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(oldExecution));
        assertFalse(result.contains(newExecution));
        
        verify(workflowExecutionRepository).findByStatus(WorkflowStatus.PAUSED);
    }
    
    @Test
    void deleteWorkflowExecution_whenInTerminalState_shouldDelete() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.COMPLETED);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act
        workflowExecutionService.deleteWorkflowExecution(id);
        
        // Assert
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).deleteById(id);
    }
    
    @Test
    void deleteWorkflowExecution_whenNotInTerminalState_shouldThrowException() {
        // Arrange
        Long id = 1L;
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution));
        
        // Act & Assert
        assertThrows(WorkflowException.class, () -> 
                workflowExecutionService.deleteWorkflowExecution(id));
        
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository, never()).deleteById(anyLong());
    }
    
    @Test
    void waitForWorkflowCompletion_whenCompletesWithinTimeout_shouldReturnCompletion() 
            throws InterruptedException, ExecutionException, TimeoutException {
        // Arrange
        Long id = 1L;
        int timeoutSeconds = 5;
        
        // Mock first call to return running, second call to return completed
        when(workflowExecutionRepository.findById(id))
                .thenReturn(Optional.of(workflowExecution))  // First call - CREATED
                .thenAnswer(invocation -> {
                    // Second call - set to COMPLETED
                    workflowExecution.setStatus(WorkflowStatus.COMPLETED);
                    return Optional.of(workflowExecution);
                });
        
        // Act
        CompletableFuture<WorkflowExecution> future = 
                workflowExecutionService.waitForWorkflowCompletion(id, timeoutSeconds);
        
        // Assert - wait for a small amount of time to allow the virtual thread to execute
        WorkflowExecution result = future.get(1, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertEquals(WorkflowStatus.COMPLETED, result.getStatus());
        verify(workflowExecutionRepository, atLeast(2)).findById(id);
    }
    
    @Test
    void init_shouldLogInitialization() {
        // Re-initialize service to trigger @PostConstruct
        workflowExecutionService.init();
        
        // Not easily testable, but ensures code coverage
        // We're not asserting anything as it just logs
    }
}