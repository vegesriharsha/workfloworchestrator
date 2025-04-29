package com.example.workfloworchestrator.util;

import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for managing task dependencies in workflow definitions
 */
@Slf4j
@Component
public class TaskDependencyUtil {

    /**
     * Validate the task dependencies in a workflow definition
     *
     * @param workflowDefinition the workflow definition to validate
     * @return true if valid, false if it contains cycles or invalid references
     */
    public boolean validateDependencies(WorkflowDefinition workflowDefinition) {
        List<TaskDefinition> tasks = workflowDefinition.getTasks();
        Map<Long, TaskDefinition> taskMap = tasks.stream()
                .collect(Collectors.toMap(TaskDefinition::getId, task -> task));

        // Check for invalid dependencies (references to non-existent tasks)
        for (TaskDefinition task : tasks) {
            if (task.hasDependencies()) {
                for (Long depId : task.getDependsOn()) {
                    if (!taskMap.containsKey(depId)) {
                        log.error("Task {} depends on non-existent task with ID: {}",
                                task.getId(), depId);
                        return false;
                    }
                }
            }
        }

        // Check for cycles using DFS
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();

        for (TaskDefinition task : tasks) {
            if (hasCycle(task.getId(), taskMap, visited, recursionStack)) {
                log.error("Cyclic dependency detected in workflow: {}",
                        workflowDefinition.getName());
                return false;
            }
        }

        return true;
    }

    /**
     * Check if there is a cycle in the dependency graph using DFS
     */
    private boolean hasCycle(Long taskId, Map<Long, TaskDefinition> taskMap,
                             Set<Long> visited, Set<Long> recursionStack) {

        // If task is already in recursion stack, we found a cycle
        if (recursionStack.contains(taskId)) {
            return true;
        }

        // If already visited and not in recursion stack, no cycle through this path
        if (visited.contains(taskId)) {
            return false;
        }

        // Mark the current task as visited and add to recursion stack
        visited.add(taskId);
        recursionStack.add(taskId);

        // Check all dependencies
        TaskDefinition task = taskMap.get(taskId);
        if (task != null && task.hasDependencies()) {
            for (Long depId : task.getDependsOn()) {
                if (hasCycle(depId, taskMap, visited, recursionStack)) {
                    return true;
                }
            }
        }

        // Remove from recursion stack when backtracking
        recursionStack.remove(taskId);

        return false;
    }

    /**
     * Sort tasks in topological order based on dependencies
     *
     * @param tasks the list of tasks to sort
     * @return a new list with tasks sorted by dependency order
     */
    public List<TaskDefinition> sortTasksByDependency(List<TaskDefinition> tasks) {
        Map<Long, TaskDefinition> taskMap = tasks.stream()
                .collect(Collectors.toMap(TaskDefinition::getId, task -> task));

        Map<Long, Set<Long>> graph = new HashMap<>();
        Map<Long, Integer> inDegree = new HashMap<>();

        // Initialize graph and in-degree
        for (TaskDefinition task : tasks) {
            Long id = task.getId();
            graph.put(id, new HashSet<>());
            inDegree.put(id, 0);
        }

        // Build the graph and calculate in-degree
        for (TaskDefinition task : tasks) {
            if (task.hasDependencies()) {
                for (Long depId : task.getDependsOn()) {
                    // Add edge from dependency to task
                    graph.get(depId).add(task.getId());
                    // Increment in-degree of task
                    inDegree.put(task.getId(), inDegree.get(task.getId()) + 1);
                }
            }
        }

        // Queue for tasks with no dependencies (in-degree = 0)
        Queue<Long> queue = new LinkedList<>();
        for (Map.Entry<Long, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        // Result list
        List<TaskDefinition> sortedTasks = new ArrayList<>();

        // Process tasks in topological order
        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            TaskDefinition currentTask = taskMap.get(currentId);
            sortedTasks.add(currentTask);

            // For each task that depends on the current task
            for (Long nextId : graph.get(currentId)) {
                // Decrement in-degree
                inDegree.put(nextId, inDegree.get(nextId) - 1);

                // If in-degree becomes 0, add to queue
                if (inDegree.get(nextId) == 0) {
                    queue.add(nextId);
                }
            }
        }

        // If not all tasks are in the result, there's a cycle
        if (sortedTasks.size() != tasks.size()) {
            log.warn("Cyclic dependency detected, returning original task order");
            return new ArrayList<>(tasks);
        }

        return sortedTasks;
    }
}
