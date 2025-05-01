package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class WorkflowExecutionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @InjectMocks
    private WorkflowExecutionController workflowExecutionController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(workflowExecutionController).build();
        objectMapper.findAndRegisterModules(); // For handling LocalDateTime
    }

    @Test
    public void testStartWorkflow() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.CREATED);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCorrelationId("test-correlation-id");

        Map<String, String> variables = new HashMap<>();
        variables.put("key1", "value1");
        variables.put("key2", "value2");

        // Setup mock behavior
        when(workflowExecutionService.startWorkflow(eq("Test Workflow"), isNull(), anyMap()))
                .thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/executions/start")
                        .param("workflowName", "Test Workflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(variables)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-id"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).startWorkflow(eq("Test Workflow"), isNull(), anyMap());
    }

    @Test
    public void testGetWorkflowExecution() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCorrelationId("test-correlation-id");

        // Setup mock behavior
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(get("/api/executions/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-id"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).getWorkflowExecution(1L);
    }

    @Test
    public void testGetWorkflowExecutionByCorrelationId() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCorrelationId("test-correlation-id");

        // Setup mock behavior
        when(workflowExecutionService.getWorkflowExecutionByCorrelationId("test-correlation-id"))
                .thenReturn(execution);

        // Execute and verify
        mockMvc.perform(get("/api/executions/correlation/test-correlation-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-id"));

        // Verify service method was called
        verify(workflowExecutionService, times(1))
                .getWorkflowExecutionByCorrelationId("test-correlation-id");
    }

    @Test
    public void testGetWorkflowExecutionsByStatus() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution1 = new WorkflowExecution();
        execution1.setId(1L);
        execution1.setWorkflowDefinition(definition);
        execution1.setStatus(WorkflowStatus.RUNNING);
        execution1.setStartedAt(LocalDateTime.now());
        execution1.setCorrelationId("test-correlation-id-1");

        WorkflowExecution execution2 = new WorkflowExecution();
        execution2.setId(2L);
        execution2.setWorkflowDefinition(definition);
        execution2.setStatus(WorkflowStatus.RUNNING);
        execution2.setStartedAt(LocalDateTime.now());
        execution2.setCorrelationId("test-correlation-id-2");

        List<WorkflowExecution> executions = Arrays.asList(execution1, execution2);

        // Setup mock behavior
        when(workflowExecutionService.getWorkflowExecutionsByStatus(WorkflowStatus.RUNNING))
                .thenReturn(executions);

        // Execute and verify
        mockMvc.perform(get("/api/executions")
                        .param("status", "RUNNING")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("RUNNING"))
                .andExpect(jsonPath("$[0].correlationId").value("test-correlation-id-1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].status").value("RUNNING"))
                .andExpect(jsonPath("$[1].correlationId").value("test-correlation-id-2"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).getWorkflowExecutionsByStatus(WorkflowStatus.RUNNING);
    }

    @Test
    public void testGetAllWorkflowExecutions() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution1 = new WorkflowExecution();
        execution1.setId(1L);
        execution1.setWorkflowDefinition(definition);
        execution1.setStatus(WorkflowStatus.RUNNING);
        execution1.setStartedAt(LocalDateTime.now());
        execution1.setCorrelationId("test-correlation-id-1");

        WorkflowExecution execution2 = new WorkflowExecution();
        execution2.setId(2L);
        execution2.setWorkflowDefinition(definition);
        execution2.setStatus(WorkflowStatus.COMPLETED);
        execution2.setStartedAt(LocalDateTime.now());
        execution2.setCorrelationId("test-correlation-id-2");

        List<WorkflowExecution> executions = Arrays.asList(execution1, execution2);

        // Setup mock behavior
        when(workflowExecutionService.getAllWorkflowExecutions()).thenReturn(executions);

        // Execute and verify
        mockMvc.perform(get("/api/executions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("RUNNING"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].status").value("COMPLETED"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).getAllWorkflowExecutions();
    }

    @Test
    public void testPauseWorkflowExecution() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.PAUSED);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCorrelationId("test-correlation-id");

        // Setup mock behavior
        when(workflowExecutionService.pauseWorkflowExecution(1L)).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/executions/1/pause")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PAUSED"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).pauseWorkflowExecution(1L);
    }

    @Test
    public void testResumeWorkflowExecution() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCorrelationId("test-correlation-id");

        // Setup mock behavior
        when(workflowExecutionService.resumeWorkflowExecution(1L)).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/executions/1/resume")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).resumeWorkflowExecution(1L);
    }

    @Test
    public void testCancelWorkflowExecution() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.CANCELLED);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCorrelationId("test-correlation-id");

        // Setup mock behavior
        when(workflowExecutionService.cancelWorkflowExecution(1L)).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/executions/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).cancelWorkflowExecution(1L);
    }

    @Test
    public void testRetryWorkflowExecution() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCorrelationId("test-correlation-id");

        // Setup mock behavior
        when(workflowExecutionService.retryWorkflowExecution(1L)).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/executions/1/retry")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).retryWorkflowExecution(1L);
    }

    @Test
    public void testRetryWorkflowExecutionSubset() throws Exception {
        // Prepare test data
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCorrelationId("test-correlation-id");

        List<Long> taskIds = Arrays.asList(1L, 2L, 3L);

        // Setup mock behavior
        when(workflowExecutionService.retryWorkflowExecutionSubset(1L, taskIds)).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/executions/1/retry-subset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // Verify service method was called
        verify(workflowExecutionService, times(1)).retryWorkflowExecutionSubset(eq(1L), anyList());
    }
}
