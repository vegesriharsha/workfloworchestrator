package com.example.workfloworchestrator.util;

import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TaskDependencyUtilTest {

    private TaskDependencyUtil taskDependencyUtil;

    @BeforeEach
    public void setup() {
        taskDependencyUtil = new TaskDependencyUtil();
    }

    @Test
    public void testValidateDependencies_ValidDependencies() {
        // Create workflow definition with valid dependencies
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setName("Test Workflow");
        workflowDefinition.setCreatedAt(LocalDateTime.now());

        // Create tasks
        TaskDefinition task1 = createTaskDefinition(1L, "Task 1", Collections.emptyList());
        TaskDefinition task2 = createTaskDefinition(2L, "Task 2", Collections.singletonList(1L));
        TaskDefinition task3 = createTaskDefinition(3L, "Task 3", Arrays.asList(1L, 2L));

        workflowDefinition.setTasks(Arrays.asList(task1, task2, task3));

        // Validate
        boolean result = taskDependencyUtil.validateDependencies(workflowDefinition);

        // Assert
        assertTrue(result, "Valid dependencies should validate successfully");
    }

    @Test
    public void testValidateDependencies_InvalidDependencies() {
        // Create workflow definition with invalid dependencies (reference to non-existent task)
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setName("Test Workflow");
        workflowDefinition.setCreatedAt(LocalDateTime.now());

        // Create tasks with dependency on non-existent task (ID 99)
        TaskDefinition task1 = createTaskDefinition(1L, "Task 1", Collections.emptyList());
        TaskDefinition task2 = createTaskDefinition(2L, "Task 2", Collections.singletonList(99L));

        workflowDefinition.setTasks(Arrays.asList(task1, task2));

        // Validate
        boolean result = taskDependencyUtil.validateDependencies(workflowDefinition);

        // Assert
        assertFalse(result, "Invalid dependencies should fail validation");
    }

    @Test
    public void testValidateDependencies_CyclicDependencies() {
        // Create workflow definition with cyclic dependencies
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setName("Test Workflow");
        workflowDefinition.setCreatedAt(LocalDateTime.now());

        // Create tasks with cyclic dependencies: 1 -> 2 -> 3 -> 1
        TaskDefinition task1 = createTaskDefinition(1L, "Task 1", Collections.singletonList(3L));
        TaskDefinition task2 = createTaskDefinition(2L, "Task 2", Collections.singletonList(1L));
        TaskDefinition task3 = createTaskDefinition(3L, "Task 3", Collections.singletonList(2L));

        workflowDefinition.setTasks(Arrays.asList(task1, task2, task3));

        // Validate
        boolean result = taskDependencyUtil.validateDependencies(workflowDefinition);

        // Assert
        assertFalse(result, "Cyclic dependencies should fail validation");
    }

    @Test
    public void testSortTasksByDependency_SimpleSequence() {
        // Create a simple sequence of tasks: 1 -> 2 -> 3
        TaskDefinition task1 = createTaskDefinition(1L, "Task 1", Collections.emptyList());
        TaskDefinition task2 = createTaskDefinition(2L, "Task 2", Collections.singletonList(1L));
        TaskDefinition task3 = createTaskDefinition(3L, "Task 3", Collections.singletonList(2L));

        List<TaskDefinition> tasks = Arrays.asList(task3, task1, task2); // Intentionally out of order

        // Sort
        List<TaskDefinition> sortedTasks = taskDependencyUtil.sortTasksByDependency(tasks);

        // Assert
        assertEquals(3, sortedTasks.size(), "Should contain all tasks");
        assertEquals(1L, sortedTasks.get(0).getId(), "First task should be Task 1");
        assertEquals(2L, sortedTasks.get(1).getId(), "Second task should be Task 2");
        assertEquals(3L, sortedTasks.get(2).getId(), "Third task should be Task 3");
    }

    @Test
    public void testSortTasksByDependency_DiamondDependencies() {
        // Create a diamond pattern: 1 -> 2 -> 4, 1 -> 3 -> 4
        TaskDefinition task1 = createTaskDefinition(1L, "Task 1", Collections.emptyList());
        TaskDefinition task2 = createTaskDefinition(2L, "Task 2", Collections.singletonList(1L));
        TaskDefinition task3 = createTaskDefinition(3L, "Task 3", Collections.singletonList(1L));
        TaskDefinition task4 = createTaskDefinition(4L, "Task 4", Arrays.asList(2L, 3L));

        List<TaskDefinition> tasks = Arrays.asList(task4, task3, task1, task2); // Intentionally out of order

        // Sort
        List<TaskDefinition> sortedTasks = taskDependencyUtil.sortTasksByDependency(tasks);

        // Assert
        assertEquals(4, sortedTasks.size(), "Should contain all tasks");
        assertEquals(1L, sortedTasks.get(0).getId(), "First task should be Task 1");

        // Task 2 and Task 3 can be in either order since they both only depend on Task 1
        assertTrue(
                (sortedTasks.get(1).getId() == 2L && sortedTasks.get(2).getId() == 3L) ||
                        (sortedTasks.get(1).getId() == 3L && sortedTasks.get(2).getId() == 2L),
                "Tasks 2 and 3 should be second and third (in any order)"
        );

        assertEquals(4L, sortedTasks.get(3).getId(), "Fourth task should be Task 4");
    }

    @Test
    public void testSortTasksByDependency_WithCycle() {
        // Create tasks with a cycle: 1 -> 2 -> 3 -> 1
        TaskDefinition task1 = createTaskDefinition(1L, "Task 1", Collections.singletonList(3L));
        TaskDefinition task2 = createTaskDefinition(2L, "Task 2", Collections.singletonList(1L));
        TaskDefinition task3 = createTaskDefinition(3L, "Task 3", Collections.singletonList(2L));

        List<TaskDefinition> tasks = Arrays.asList(task1, task2, task3);

        // Sort - with cycle it should return original order
        List<TaskDefinition> sortedTasks = taskDependencyUtil.sortTasksByDependency(tasks);

        // Assert
        assertEquals(3, sortedTasks.size(), "Should contain all tasks");
        // Should return original order when there's a cycle
        assertEquals(task1.getId(), sortedTasks.get(0).getId(), "Should maintain original order");
        assertEquals(task2.getId(), sortedTasks.get(1).getId(), "Should maintain original order");
        assertEquals(task3.getId(), sortedTasks.get(2).getId(), "Should maintain original order");
    }

    @Test
    public void testSortTasksByDependency_NoDependencies() {
        // Create tasks with no dependencies
        TaskDefinition task1 = createTaskDefinition(1L, "Task 1", Collections.emptyList());
        TaskDefinition task2 = createTaskDefinition(2L, "Task 2", Collections.emptyList());
        TaskDefinition task3 = createTaskDefinition(3L, "Task 3", Collections.emptyList());

        List<TaskDefinition> tasks = Arrays.asList(task1, task2, task3);

        // Sort
        List<TaskDefinition> sortedTasks = taskDependencyUtil.sortTasksByDependency(tasks);

        // Assert
        assertEquals(3, sortedTasks.size(), "Should contain all tasks");
        // No dependencies, should maintain original order
        assertEquals(task1.getId(), sortedTasks.get(0).getId(), "Should maintain original order");
        assertEquals(task2.getId(), sortedTasks.get(1).getId(), "Should maintain original order");
        assertEquals(task3.getId(), sortedTasks.get(2).getId(), "Should maintain original order");
    }

    @Test
    public void testSortTasksByDependency_ComplexDependencies() {
        // Create tasks with more complex dependencies
        TaskDefinition task1 = createTaskDefinition(1L, "Task 1", Collections.emptyList());
        TaskDefinition task2 = createTaskDefinition(2L, "Task 2", Collections.emptyList());
        TaskDefinition task3 = createTaskDefinition(3L, "Task 3", Arrays.asList(1L, 2L));
        TaskDefinition task4 = createTaskDefinition(4L, "Task 4", Collections.singletonList(2L));
        TaskDefinition task5 = createTaskDefinition(5L, "Task 5", Arrays.asList(3L, 4L));
        TaskDefinition task6 = createTaskDefinition(6L, "Task 6", Collections.singletonList(5L));

        List<TaskDefinition> tasks = Arrays.asList(task6, task5, task4, task3, task2, task1); // Reverse order

        // Sort
        List<TaskDefinition> sortedTasks = taskDependencyUtil.sortTasksByDependency(tasks);

        // Assert
        assertEquals(6, sortedTasks.size(), "Should contain all tasks");

        // Check for valid topological order
        // 1 and 2 can be in either order (no dependencies between them)
        assertTrue(sortedTasks.indexOf(task1) < sortedTasks.indexOf(task3), "Task 1 should come before Task 3");
        assertTrue(sortedTasks.indexOf(task2) < sortedTasks.indexOf(task3), "Task 2 should come before Task 3");
        assertTrue(sortedTasks.indexOf(task2) < sortedTasks.indexOf(task4), "Task 2 should come before Task 4");
        assertTrue(sortedTasks.indexOf(task3) < sortedTasks.indexOf(task5), "Task 3 should come before Task 5");
        assertTrue(sortedTasks.indexOf(task4) < sortedTasks.indexOf(task5), "Task 4 should come before Task 5");
        assertTrue(sortedTasks.indexOf(task5) < sortedTasks.indexOf(task6), "Task 5 should come before Task 6");
    }

    // Helper method to create TaskDefinition objects
    private TaskDefinition createTaskDefinition(Long id, String name, List<Long> dependsOn) {
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setId(id);
        taskDefinition.setName(name);

        // Make sure we create a new list to avoid shared references that could cause test issues
        taskDefinition.setDependsOn(new ArrayList<>(dependsOn));

        return taskDefinition;
    }
}
