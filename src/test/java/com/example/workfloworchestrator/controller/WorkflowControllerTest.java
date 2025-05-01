package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.service.WorkflowService;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class WorkflowControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private WorkflowController workflowController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(workflowController).build();
        objectMapper.findAndRegisterModules(); // For handling LocalDateTime and other Java 8 types
    }

    @Test
    public void testGetAllWorkflowDefinitions() throws Exception {
        // Prepare test data
        WorkflowDefinition workflow1 = new WorkflowDefinition();
        workflow1.setId(1L);
        workflow1.setName("Workflow 1");
        workflow1.setCreatedAt(LocalDateTime.now());

        WorkflowDefinition workflow2 = new WorkflowDefinition();
        workflow2.setId(2L);
        workflow2.setName("Workflow 2");
        workflow2.setCreatedAt(LocalDateTime.now());

        List<WorkflowDefinition> workflows = Arrays.asList(workflow1, workflow2);

        // Setup mock behavior
        when(workflowService.getAllWorkflowDefinitions()).thenReturn(workflows);

        // Execute and verify
        mockMvc.perform(get("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Workflow 1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Workflow 2"));

        // Verify service method was called
        verify(workflowService, times(1)).getAllWorkflowDefinitions();
    }

    @Test
    public void testGetWorkflowDefinitionById() throws Exception {
        // Prepare test data
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setId(1L);
        workflow.setName("Test Workflow");
        workflow.setDescription("A test workflow");
        workflow.setCreatedAt(LocalDateTime.now());

        // Setup mock behavior
        when(workflowService.getWorkflowDefinition(1L)).thenReturn(Optional.of(workflow));

        // Execute and verify
        mockMvc.perform(get("/api/workflows/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Workflow"))
                .andExpect(jsonPath("$.description").value("A test workflow"));

        // Verify service method was called
        verify(workflowService, times(1)).getWorkflowDefinition(1L);
    }

    @Test
    public void testGetWorkflowDefinitionByIdNotFound() throws Exception {
        // Setup mock behavior
        when(workflowService.getWorkflowDefinition(99L)).thenReturn(Optional.empty());

        // Execute and verify
        mockMvc.perform(get("/api/workflows/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify service method was called
        verify(workflowService, times(1)).getWorkflowDefinition(99L);
    }

    @Test
    public void testCreateWorkflowDefinition() throws Exception {
        // Prepare test data
        WorkflowDefinition inputWorkflow = new WorkflowDefinition();
        inputWorkflow.setName("New Workflow");
        inputWorkflow.setDescription("A new workflow");

        WorkflowDefinition savedWorkflow = new WorkflowDefinition();
        savedWorkflow.setId(1L);
        savedWorkflow.setName("New Workflow");
        savedWorkflow.setDescription("A new workflow");
        savedWorkflow.setCreatedAt(LocalDateTime.now());

        // Setup mock behavior
        when(workflowService.createWorkflowDefinition(any(WorkflowDefinition.class))).thenReturn(savedWorkflow);

        // Execute and verify
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputWorkflow)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("New Workflow"))
                .andExpect(jsonPath("$.description").value("A new workflow"));

        // Verify service method was called
        verify(workflowService, times(1)).createWorkflowDefinition(any(WorkflowDefinition.class));
    }

    @Test
    public void testUpdateWorkflowDefinition() throws Exception {
        // Prepare test data
        WorkflowDefinition inputWorkflow = new WorkflowDefinition();
        inputWorkflow.setName("Updated Workflow");
        inputWorkflow.setDescription("An updated workflow");

        WorkflowDefinition updatedWorkflow = new WorkflowDefinition();
        updatedWorkflow.setId(1L);
        updatedWorkflow.setName("Updated Workflow");
        updatedWorkflow.setDescription("An updated workflow");
        updatedWorkflow.setCreatedAt(LocalDateTime.now());

        // Setup mock behavior
        when(workflowService.updateWorkflowDefinition(eq(1L), any(WorkflowDefinition.class)))
                .thenReturn(updatedWorkflow);

        // Execute and verify
        mockMvc.perform(put("/api/workflows/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputWorkflow)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Workflow"))
                .andExpect(jsonPath("$.description").value("An updated workflow"));

        // Verify service method was called
        verify(workflowService, times(1)).updateWorkflowDefinition(eq(1L), any(WorkflowDefinition.class));
    }

    @Test
    public void testDeleteWorkflowDefinition() throws Exception {
        // Execute and verify
        mockMvc.perform(delete("/api/workflows/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify service method was called
        verify(workflowService, times(1)).deleteWorkflowDefinition(1L);
    }

    @Test
    public void testGetLatestWorkflowDefinition() throws Exception {
        // Prepare test data
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setId(1L);
        workflow.setName("Test Workflow");
        workflow.setVersion("1.0.0");
        workflow.setCreatedAt(LocalDateTime.now());

        // Setup mock behavior
        when(workflowService.getLatestWorkflowDefinition("Test Workflow")).thenReturn(Optional.of(workflow));

        // Execute and verify
        mockMvc.perform(get("/api/workflows/name/Test Workflow")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Workflow"))
                .andExpect(jsonPath("$.version").value("1.0.0"));

        // Verify service method was called
        verify(workflowService, times(1)).getLatestWorkflowDefinition("Test Workflow");
    }

    @Test
    public void testGetWorkflowDefinitionByNameAndVersion() throws Exception {
        // Prepare test data
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setId(1L);
        workflow.setName("Test Workflow");
        workflow.setVersion("1.0.0");
        workflow.setCreatedAt(LocalDateTime.now());

        // Setup mock behavior
        when(workflowService.getWorkflowDefinition("Test Workflow", "1.0.0")).thenReturn(Optional.of(workflow));

        // Execute and verify
        mockMvc.perform(get("/api/workflows/name/Test Workflow/version/1.0.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Workflow"))
                .andExpect(jsonPath("$.version").value("1.0.0"));

        // Verify service method was called
        verify(workflowService, times(1)).getWorkflowDefinition("Test Workflow", "1.0.0");
    }
}
