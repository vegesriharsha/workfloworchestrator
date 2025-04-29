package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Graph execution strategy
 * Executes tasks based on their dependencies and execution groups
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphExecutionStrategy implements ExecutionStrategy {

    private final TaskExecutionService taskExecutionService;
    private final WorkflowExecutionService workflowExecutionService;

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> allTasks = definition.getTasks();

            // Build dependency graph
            Map<Long, Set<Long>> dependencies = buildDependencyGraph(allTasks);

            // Find tasks with no dependencies (roots)
            List<TaskDefinition> rootTasks = findRootTasks(allTasks, dependencies);

            // Start execution with root tasks
            executeGraph(workflowExecution, allTasks, dependencies, rootTasks, resultFuture);

        } catch (Exception e) {
            log.error("Error in graph execution strategy", e);
            resultFuture.completeExceptionally(new WorkflowException("Graph execution failed: " + e.getMessage(), e));
            updateWorkflowOnError(workflowExecution, e);
        }

        return resultFuture;
    }

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List<Long> taskIds) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();

            // Filter tasks based on provided IDs
            List<TaskDefinition> selectedTasks = definition.getTasks().stream()
                    .filter(task -> taskIds.contains(task.getId()))
                    .collect(Collectors.toList());

            if (selectedTasks.isEmpty()) {
                resultFuture.complete(WorkflowStatus.COMPLETED);
                return resultFuture;
            }

            // When executing a subset, we ignore dependencies outside the subset
            // but respect dependencies within the subset

            // Build partial dependency graph for the subset
            Map<Long, Set<Long>> partialDependencies = buildPartialDependencyGraph(selectedTasks, taskIds);

            // Find root tasks within the subset
            List<TaskDefinition> rootTasks = findRootTasks(selectedTasks, partialDependencies);

            // Start execution with root tasks
            executeGraph(workflowExecution, selectedTasks, partialDependencies, rootTasks, resultFuture);

        } catch (Exception e) {
            log.error("Error in graph subset execution", e);
            resultFuture.completeExceptionally(new WorkflowException("Graph subset execution failed: " + e.getMessage(), e));
            updateWorkflowOnError(workflowExecution, e);
        }

        return resultFuture;
    }

    /**
     * Build the dependency graph for all tasks
     *
     * @param tasks the list of task definitions
     * @return a map where key is task ID and value is set of task IDs it depends on
     */
    private Map<Long, Set<Long>> buildDependencyGraph(List<TaskDefinition> tasks) {
        Map<Long, Set<Long>> dependencyGraph = new HashMap<>();

        // Initialize the graph with empty dependency sets
        for (TaskDefinition task : tasks) {
            dependencyGraph.put(task.getId(), new HashSet<>());
        }

        // Populate dependencies
        for (TaskDefinition task : tasks) {
            if (task.hasDependencies()) {
                dependencyGraph.get(task.getId()).addAll(task.getDependsOn());
            }
        }

        return dependencyGraph;
    }

    /**
     * Build a partial dependency graph for a subset of tasks
     *
     * @param selectedTasks the subset of tasks
     * @param selectedIds the IDs of the selected tasks
     * @return a map where key is task ID and value is set of task IDs it depends on (limited to the subset)
     */
    private Map<Long, Set<Long>> buildPartialDependencyGraph(List<TaskDefinition> selectedTasks, List<Long> selectedIds) {
        Map<Long, Set<Long>> partialGraph = new HashMap<>();

        // Initialize the graph with empty dependency sets
        for (TaskDefinition task : selectedTasks) {
            partialGraph.put(task.getId(), new HashSet<>());
        }

        // Populate dependencies, but only if they are within the subset
        for (TaskDefinition task : selectedTasks) {
            if (task.hasDependencies()) {
                Set<Long> filteredDependencies = task.getDependsOn().stream()
                        .filter(selectedIds::contains)
                        .collect(Collectors.toSet());

                partialGraph.get(task.getId()).addAll(filteredDependencies);
            }
        }

        return partialGraph;
    }

    /**
     * Find tasks with no dependencies (root tasks)
     *
     * @param tasks the list of task definitions
     * @param dependencies the dependency graph
     * @return list of tasks with no dependencies
     */
    private List<TaskDefinition> findRootTasks(List<TaskDefinition> tasks, Map<Long, Set<Long>> dependencies) {
        return tasks.stream()
                .filter(task -> {
                    Set<Long> deps = dependencies.get(task.getId());
                    return deps == null || deps.isEmpty();
                })
                .collect(Collectors.toList());
    }

    /**
     * Execute the workflow graph starting with the given batch of tasks
     *
     * @param workflowExecution the workflow execution
     * @param allTasks all task definitions in the workflow
     * @param dependencies the dependency graph
     * @param currentBatch the current batch of tasks to execute
     * @param resultFuture the result future to complete
     */
    private void executeGraph(WorkflowExecution workflowExecution,
                              List<TaskDefinition> allTasks,
                              Map<Long, Set<Long>> dependencies,
                              List<TaskDefinition> currentBatch,
                              CompletableFuture<WorkflowStatus> resultFuture) {

        if (currentBatch.isEmpty()) {
            // No more tasks to execute, workflow is complete
            resultFuture.complete(WorkflowStatus.COMPLETED);
            return;
        }

        // Group tasks in current batch by execution group
        Map<String, List<TaskDefinition>> groupedTasks = currentBatch.stream()
                .collect(Collectors.groupingBy(TaskDefinition::getExecutionGroup));

        // Track task execution futures and results
        Map<Long, CompletableFuture<TaskExecution>> taskFutures = new ConcurrentHashMap<>();
        Set<Long> completedTaskIds = Collections.synchronizedSet(new HashSet<>());

        // Execute each group appropriately
        List<CompletableFuture<Void>> groupFutures = new ArrayList<>();

        for (Map.Entry<String, List<TaskDefinition>> entry : groupedTasks.entrySet()) {
            String groupName = entry.getKey();
            List<TaskDefinition> groupTasks = entry.getValue();

            // Determine if this group should execute in parallel
            boolean executeInParallel = isParallelGroup(groupName, groupTasks);

            CompletableFuture<Void> groupFuture;
            if (executeInParallel) {
                groupFuture = executeTasksInParallel(workflowExecution, groupTasks, taskFutures);
            } else {
                groupFuture = executeTasksSequentially(workflowExecution, groupTasks, taskFutures);
            }

            groupFutures.add(groupFuture);
        }

        // When all groups are complete
        CompletableFuture<Void> allGroupsFuture = CompletableFuture.allOf(
                groupFutures.toArray(new CompletableFuture[0]));

        allGroupsFuture.whenComplete((ignored, throwable) -> {
            try {
                if (throwable != null) {
                    log.error("Error executing task groups", throwable);
                    resultFuture.completeExceptionally(
                            new WorkflowException("Task group execution failed: " + throwable.getMessage(), throwable));

                    updateWorkflowOnError(workflowExecution, throwable);
                    return;
                }

                // Process results for the completed tasks
                for (Map.Entry<Long, CompletableFuture<TaskExecution>> entry : taskFutures.entrySet()) {
                    try {
                        TaskExecution taskExecution = entry.getValue().join();
                        Long taskId = entry.getKey();

                        // Add to completed tasks
                        completedTaskIds.add(taskId);

                        // Update workflow variables with task outputs if task completed successfully
                        if (taskExecution.getStatus() == TaskStatus.COMPLETED) {
                            Map<String, String> variables = workflowExecution.getVariables();
                            variables.putAll(taskExecution.getOutputs());
                            workflowExecution.setVariables(variables);
                        }
                        // If task failed, check if there's custom error handling
                        else if (taskExecution.getStatus() == TaskStatus.FAILED) {
                            TaskDefinition taskDefinition = taskExecution.getTaskDefinition();

                            // Check for specific error handling path
                            if (taskDefinition.getNextTaskOnFailure() != null) {
                                // Find the error handler task
                                Optional<TaskDefinition> errorHandler = allTasks.stream()
                                        .filter(t -> t.getId().equals(taskDefinition.getNextTaskOnFailure()))
                                        .findFirst();

                                if (errorHandler.isPresent()) {
                                    // Execute the error handler path
                                    executeGraph(
                                            workflowExecution,
                                            allTasks,
                                            dependencies,
                                            List.of(errorHandler.get()),
                                            resultFuture);
                                    return;
                                }
                            }

                            // If no specific error handler, check if we should continue despite errors
                            if (!shouldContinueOnError(workflowExecution)) {
                                workflowExecution.setErrorMessage("Task failed: " + taskExecution.getErrorMessage());
                                workflowExecutionService.updateWorkflowExecutionStatus(
                                        workflowExecution.getId(), WorkflowStatus.FAILED);

                                resultFuture.complete(WorkflowStatus.FAILED);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing task result", e);
                        // Continue with other tasks
                    }
                }

                // Save the updated workflow variables
                workflowExecutionService.save(workflowExecution);

                // Find the next batch of tasks whose dependencies are now satisfied
                List<TaskDefinition> nextBatch = findNextBatchOfTasks(allTasks, dependencies, completedTaskIds);

                // Continue with the next batch
                executeGraph(workflowExecution, allTasks, dependencies, nextBatch, resultFuture);

            } catch (Exception e) {
                log.error("Error processing task group completion", e);
                resultFuture.completeExceptionally(
                        new WorkflowException("Error processing task group completion: " + e.getMessage(), e));

                updateWorkflowOnError(workflowExecution, e);
            }
        });
    }

    /**
     * Determine if a group should execute in parallel
     */
    private boolean isParallelGroup(String groupName, List<TaskDefinition> groupTasks) {
        // Check if all tasks in the group allow parallel execution
        return groupTasks.stream().allMatch(TaskDefinition::isParallelExecution);
    }

    /**
     * Execute a group of tasks in parallel
     */
    private CompletableFuture<Void> executeTasksInParallel(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> tasks,
            Map<Long, CompletableFuture<TaskExecution>> taskFutures) {

        List<CompletableFuture<TaskExecution>> futures = new ArrayList<>();

        for (TaskDefinition taskDefinition : tasks) {
            // Check conditional expression (if any)
            if (!evaluateCondition(taskDefinition, workflowExecution)) {
                log.info("Skipping task {} due to condition not met", taskDefinition.getName());
                continue;
            }

            // Prepare inputs from workflow variables
            Map<String, String> inputs = new HashMap<>(workflowExecution.getVariables());

            // Create task execution
            TaskExecution taskExecution = taskExecutionService.createTaskExecution(
                    workflowExecution, taskDefinition, inputs);

            // Check if task requires user review (can't be parallel)
            if (taskDefinition.isRequireUserReview()) {
                CompletableFuture<TaskExecution> userReviewFuture = handleUserReviewTask(workflowExecution, taskExecution);
                futures.add(userReviewFuture);
                taskFutures.put(taskDefinition.getId(), userReviewFuture);
            } else {
                // Execute the task
                CompletableFuture<TaskExecution> future = taskExecutionService.executeTask(taskExecution.getId());
                futures.add(future);
                taskFutures.put(taskDefinition.getId(), future);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Execute a group of tasks sequentially
     */
    private CompletableFuture<Void> executeTasksSequentially(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> tasks,
            Map<Long, CompletableFuture<TaskExecution>> taskFutures) {

        CompletableFuture<Void> groupFuture = new CompletableFuture<>();

        // Execute tasks one by one
        executeTaskSequence(workflowExecution, tasks, 0, taskFutures, groupFuture);

        return groupFuture;
    }

    /**
     * Execute tasks sequentially, one after another
     */
    private void executeTaskSequence(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> tasks,
            int index,
            Map<Long, CompletableFuture<TaskExecution>> taskFutures,
            CompletableFuture<Void> groupFuture) {

        if (index >= tasks.size()) {
            // All tasks in sequence completed
            groupFuture.complete(null);
            return;
        }

        TaskDefinition taskDefinition = tasks.get(index);

        // Check conditional expression (if any)
        if (!evaluateCondition(taskDefinition, workflowExecution)) {
            log.info("Skipping task {} due to condition not met", taskDefinition.getName());
            executeTaskSequence(workflowExecution, tasks, index + 1, taskFutures, groupFuture);
            return;
        }

        // Prepare inputs from workflow variables
        Map<String, String> inputs = new HashMap<>(workflowExecution.getVariables());

        // Create task execution
        TaskExecution taskExecution = taskExecutionService.createTaskExecution(
                workflowExecution, taskDefinition, inputs);

        CompletableFuture<TaskExecution> taskFuture;

        // Check if task requires user review
        if (taskDefinition.isRequireUserReview()) {
            taskFuture = handleUserReviewTask(workflowExecution, taskExecution);
        } else {
            // Execute the task
            taskFuture = taskExecutionService.executeTask(taskExecution.getId());
        }

        // Store the task future
        taskFutures.put(taskDefinition.getId(), taskFuture);

        // When this task completes, execute the next one
        taskFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                groupFuture.completeExceptionally(throwable);
                return;
            }

            try {
                // If the task completed successfully, update workflow variables
                if (result.getStatus() == TaskStatus.COMPLETED) {
                    Map<String, String> variables = workflowExecution.getVariables();
                    variables.putAll(result.getOutputs());
                    workflowExecution.setVariables(variables);
                    workflowExecutionService.save(workflowExecution);
                }

                // Continue with next task in sequence
                executeTaskSequence(workflowExecution, tasks, index + 1, taskFutures, groupFuture);
            } catch (Exception e) {
                groupFuture.completeExceptionally(e);
            }
        });
    }

    /**
     * Handle a task that requires user review
     */
    private CompletableFuture<TaskExecution> handleUserReviewTask(
            WorkflowExecution workflowExecution,
            TaskExecution taskExecution) {

        CompletableFuture<TaskExecution> future = new CompletableFuture<>();

        try {
            // Move workflow to await review
            workflowExecutionService.updateWorkflowExecutionStatus(
                    workflowExecution.getId(), WorkflowStatus.AWAITING_USER_REVIEW);

            // Create review point
            taskExecutionService.createUserReviewPoint(taskExecution.getId());

            // The future will be completed when the user submits their review
            // For now, we just return the task in its current state
            future.complete(taskExecution);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Find the next batch of tasks whose dependencies are now satisfied
     */
    private List<TaskDefinition> findNextBatchOfTasks(
            List<TaskDefinition> allTasks,
            Map<Long, Set<Long>> dependencies,
            Set<Long> completedTaskIds) {

        return allTasks.stream()
                .filter(task -> !completedTaskIds.contains(task.getId())) // Not already executed
                .filter(task -> {
                    // Check if all dependencies are satisfied
                    Set<Long> deps = dependencies.get(task.getId());
                    return deps.isEmpty() || completedTaskIds.containsAll(deps);
                })
                .collect(Collectors.toList());
    }

    /**
     * Evaluate a task's conditional expression
     */
    private boolean evaluateCondition(TaskDefinition taskDefinition, WorkflowExecution workflowExecution) {
        String condition = taskDefinition.getConditionalExpression();

        if (condition == null || condition.trim().isEmpty()) {
            // No condition, always execute
            return true;
        }

        // Simple expression evaluation for demo, in production use a proper expression engine
        try {
            // Get workflow variables
            Map<String, String> variables = workflowExecution.getVariables();

            // Implement simple expression evaluation logic
            // For production, use Spring Expression Language (SpEL) or a similar library
            return evaluateSimpleExpression(condition, variables);
        } catch (Exception e) {
            log.error("Error evaluating condition for task {}: {}",
                    taskDefinition.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Evaluate a simple boolean expression
     * For demonstration purposes only - in production use a proper expression engine
     */
    private boolean evaluateSimpleExpression(String expression, Map<String, String> variables) {
        // Very simplified expression evaluation - handles only basic AND/OR expressions
        if (expression.contains("&&")) {
            String[] parts = expression.split("&&");
            return Arrays.stream(parts)
                    .allMatch(part -> evaluateSimpleExpression(part.trim(), variables));
        } else if (expression.contains("||")) {
            String[] parts = expression.split("\\|\\|");
            return Arrays.stream(parts)
                    .anyMatch(part -> evaluateSimpleExpression(part.trim(), variables));
        } else if (expression.startsWith("${") && expression.endsWith("}")) {
            // Variable reference like ${task1.output.success}
            String key = expression.substring(2, expression.length() - 1);
            String value = variables.get(key);
            return "true".equalsIgnoreCase(value);
        } else {
            // Direct boolean value
            return Boolean.parseBoolean(expression);
        }
    }

    /**
     * Update workflow status on error
     */
    private void updateWorkflowOnError(WorkflowExecution workflowExecution, Throwable throwable) {
        workflowExecution.setErrorMessage(throwable.getMessage());
        workflowExecutionService.updateWorkflowExecutionStatus(
                workflowExecution.getId(), WorkflowStatus.FAILED);
    }

    /**
     * Determine if the workflow should continue execution despite errors
     */
    private boolean shouldContinueOnError(WorkflowExecution workflowExecution) {
        // Check workflow configuration for continue-on-error flag
        Map<String, String> variables = workflowExecution.getVariables();
        return "true".equalsIgnoreCase(variables.getOrDefault("continueOnError", "false"));
    }
}
