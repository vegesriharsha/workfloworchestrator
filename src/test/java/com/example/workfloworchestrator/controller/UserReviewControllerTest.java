package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.controller.UserReviewController.EnhancedReviewRequest;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.UserReviewService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserReviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserReviewService userReviewService;

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @Mock
    private TaskExecutionService taskExecutionService;

    @InjectMocks
    private UserReviewController userReviewController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(userReviewController).build();
        objectMapper.findAndRegisterModules(); // For handling LocalDateTime
    }

    @Test
    public void testGetReviewOptions() throws Exception {
        // Prepare test data
        Long reviewPointId = 1L;

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.AWAITING_USER_REVIEW);

        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setId(1L);
        taskDefinition.setName("Review Task");

        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setId(1L);
        taskExecution.setTaskDefinition(taskDefinition);
        taskExecution.setStatus(TaskStatus.PENDING);

        UserReviewPoint reviewPoint = new UserReviewPoint();
        reviewPoint.setId(reviewPointId);
        reviewPoint.setTaskExecutionId(taskExecution.getId());
        reviewPoint.setCreatedAt(LocalDateTime.now());

        List<UserReviewPoint> reviewPoints = new ArrayList<>();
        reviewPoints.add(reviewPoint);
        execution.setReviewPoints(reviewPoints);

        List<TaskExecution> allTasks = new ArrayList<>();
        allTasks.add(taskExecution);

        // Setup mock behavior
        when(userReviewService.getWorkflowExecutionByReviewPointId(reviewPointId)).thenReturn(execution);
        when(taskExecutionService.getTaskExecution(taskExecution.getId())).thenReturn(taskExecution);
        when(taskExecutionService.getTaskExecutionsForWorkflow(execution.getId())).thenReturn(allTasks);

        // Execute and verify
        mockMvc.perform(get("/api/reviews/" + reviewPointId + "/options")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewPointId").value(reviewPointId))
                .andExpect(jsonPath("$.taskName").value("Review Task"))
                .andExpect(jsonPath("$.taskStatus").value("PENDING"))
                .andExpect(jsonPath("$.priorTasks").isArray())
                .andExpect(jsonPath("$.previousReviewPoints").isArray());

        // Verify service methods were called
        verify(userReviewService, times(1)).getWorkflowExecutionByReviewPointId(reviewPointId);
        verify(taskExecutionService, times(1)).getTaskExecution(taskExecution.getId());
        verify(taskExecutionService, times(1)).getTaskExecutionsForWorkflow(execution.getId());
    }

    @Test
    public void testGetReviewOptionsReviewPointNotFound() throws Exception {
        // Setup mock behavior
        Long reviewPointId = 99L;

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setReviewPoints(new ArrayList<>());

        when(userReviewService.getWorkflowExecutionByReviewPointId(reviewPointId)).thenReturn(execution);

        // Execute and verify - should throw exception
        mockMvc.perform(get("/api/reviews/" + reviewPointId + "/options")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()); // Assuming a 400 for WorkflowException
    }

    @Test
    public void testSubmitReview() throws Exception {
        // Prepare test data
        Long reviewPointId = 1L;

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.RUNNING);

        EnhancedReviewRequest request = new EnhancedReviewRequest();
        request.setDecision(UserReviewPoint.ReviewDecision.APPROVE);
        request.setReviewer("testUser");
        request.setComment("Looks good");

        // Setup mock behavior
        when(userReviewService.submitUserReview(
                eq(reviewPointId),
                eq(UserReviewPoint.ReviewDecision.APPROVE),
                eq("testUser"),
                eq("Looks good"),
                anyList(),
                any(TaskStatus.class)
        )).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/reviews/" + reviewPointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // Verify service method was called
        verify(userReviewService, times(1)).submitUserReview(
                eq(reviewPointId),
                eq(UserReviewPoint.ReviewDecision.APPROVE),
                eq("testUser"),
                eq("Looks good"),
                anyList(),
                any(TaskStatus.class)
        );
    }

    @Test
    public void testSubmitReviewWithTaskReplay() throws Exception {
        // Prepare test data
        Long reviewPointId = 1L;

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.RUNNING);

        EnhancedReviewRequest request = new EnhancedReviewRequest();
        request.setDecision(UserReviewPoint.ReviewDecision.REPLAY_SUBSET);
        request.setReviewer("testUser");
        request.setComment("Need to replay these tasks");
        request.setReplayTaskIds(Arrays.asList(1L, 2L, 3L));

        // Setup mock behavior
        when(userReviewService.submitUserReview(
                eq(reviewPointId),
                eq(UserReviewPoint.ReviewDecision.REPLAY_SUBSET),
                eq("testUser"),
                eq("Need to replay these tasks"),
                eq(Arrays.asList(1L, 2L, 3L)),
                any(TaskStatus.class)
        )).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/reviews/" + reviewPointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // Verify service method was called
        verify(userReviewService, times(1)).submitUserReview(
                eq(reviewPointId),
                eq(UserReviewPoint.ReviewDecision.REPLAY_SUBSET),
                eq("testUser"),
                eq("Need to replay these tasks"),
                eq(Arrays.asList(1L, 2L, 3L)),
                any(TaskStatus.class)
        );
    }

    @Test
    public void testSubmitReviewWithStatusOverride() throws Exception {
        // Prepare test data
        Long reviewPointId = 1L;

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(1L);
        definition.setName("Test Workflow");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setStatus(WorkflowStatus.RUNNING);

        EnhancedReviewRequest request = new EnhancedReviewRequest();
        request.setDecision(UserReviewPoint.ReviewDecision.OVERRIDE);
        request.setReviewer("testUser");
        request.setComment("Overriding to completed");
        request.setOverrideStatus(TaskStatus.COMPLETED);

        // Setup mock behavior
        when(userReviewService.submitUserReview(
                eq(reviewPointId),
                eq(UserReviewPoint.ReviewDecision.OVERRIDE),
                eq("testUser"),
                eq("Overriding to completed"),
                anyList(),
                eq(TaskStatus.COMPLETED)
        )).thenReturn(execution);

        // Execute and verify
        mockMvc.perform(post("/api/reviews/" + reviewPointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // Verify service method was called
        verify(userReviewService, times(1)).submitUserReview(
                eq(reviewPointId),
                eq(UserReviewPoint.ReviewDecision.OVERRIDE),
                eq("testUser"),
                eq("Overriding to completed"),
                anyList(),
                eq(TaskStatus.COMPLETED)
        );
    }
}
