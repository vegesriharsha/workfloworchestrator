# Graph-Based Workflow Task Dependencies

This document describes how to implement and use the graph-based execution model for complex workflows in the Workflow Orchestrator.

## Overview

The graph-based execution model allows you to:

1. Define explicit dependencies between tasks
2. Group tasks for parallel execution
3. Use conditional expressions to control task execution
4. Create complex workflow patterns including parallel, sequential, and conditional paths

## Task Dependencies

Tasks can specify their dependencies using the `dependsOn` field, which contains a list of task IDs that must complete before this task can execute.

```java
TaskDefinition task1 = new TaskDefinition();
task1.setName("task1");
task1.setDependsOn(Collections.emptyList()); // No dependencies, start immediately

TaskDefinition task2 = new TaskDefinition();
task2.setName("task2");
task2.setDependsOn(Collections.emptyList()); // No dependencies, can run parallel with task1

TaskDefinition task3 = new TaskDefinition();
task3.setName("task3");
task3.setDependsOn(List.of(task1.getId(), task2.getId())); // Depends on both task1 and task2
task3.setConditionalExpression("${task1.output.success} && ${task2.output.success}");
```

## Execution Groups

Tasks can be organized into execution groups using the `executionGroup` field. Tasks in the same execution group can be executed in parallel or sequentially depending on the `parallelExecution` setting.

```java
// Tasks in the same group can run in parallel
TaskDefinition task1 = new TaskDefinition();
task1.setName("task1");
task1.setExecutionGroup("group1");
task1.setParallelExecution(true);

TaskDefinition task2 = new TaskDefinition();
task2.setName("task2");
task2.setExecutionGroup("group1");
task2.setParallelExecution(true);

// Tasks in a sequential group
TaskDefinition task3 = new TaskDefinition();
task3.setName("task3");
task3.setExecutionGroup("group2");
task3.setParallelExecution(false);
```

## Conditional Expressions

Tasks can include conditional expressions that determine whether they should be executed. The condition is evaluated using workflow variables, which include outputs from previous tasks.

```java
TaskDefinition task = new TaskDefinition();
task.setName("conditional-task");
task.setConditionalExpression("${previousTask.output.success} && ${data.value > 100}");
task.setDependsOn(List.of(previousTask.getId()));
```

Supported operators in conditional expressions:
- `&&` (AND)
- `||` (OR)
- `!` (NOT)
- Comparison operators (`>`, `<`, `==`, `!=`, `>=`, `<=`)

Variables can be referenced using the `${variableName}` syntax, where the variable name can include outputs from previous tasks.

## Error Handling

Tasks can specify a task to execute if they fail using the `nextTaskOnFailure` field.

```java
TaskDefinition task = new TaskDefinition();
task.setName("main-task");

TaskDefinition errorHandler = new TaskDefinition();
errorHandler.setName("error-handler");

// Link the error handler
task.setNextTaskOnFailure(errorHandler.getId());
```

## Execution Strategies

The Workflow Orchestrator supports the following execution strategies:

1. **SEQUENTIAL**: Executes tasks one after another in order of execution order
2. **PARALLEL**: Executes independent tasks in parallel, grouped by execution order
3. **CONDITIONAL**: Determines task execution based on conditions
4. **GRAPH**: Executes tasks based on their explicitly defined dependencies
5. **HYBRID**: Combines task dependencies with execution groups for maximum flexibility

Choose the appropriate strategy based on your workflow's complexity:

```java
WorkflowDefinition workflow = new WorkflowDefinition();
workflow.setName("my-workflow");
workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.HYBRID);
```

## Common Workflow Patterns

### Sequential Workflow

Tasks execute one after another:

```java
TaskDefinition task1 = new TaskDefinition();
TaskDefinition task2 = new TaskDefinition();
TaskDefinition task3 = new TaskDefinition();

// Chain dependencies
task2.setDependsOn(List.of(task1.getId()));
task3.setDependsOn(List.of(task2.getId()));
```

### Parallel Workflow

Tasks execute in parallel:

```java
TaskDefinition task1 = new TaskDefinition();
TaskDefinition task2 = new TaskDefinition();
TaskDefinition task3 = new TaskDefinition();

// No dependencies between tasks
// All in the same execution group
task1.setExecutionGroup("parallel-group");
task2.setExecutionGroup("parallel-group");
task3.setExecutionGroup("parallel-group");
```

### Fork-Join Workflow

Tasks branch out and then sync back:

```java
TaskDefinition start = new TaskDefinition();

// Fork into parallel paths
TaskDefinition path1Task1 = new TaskDefinition();
TaskDefinition path1Task2 = new TaskDefinition();
TaskDefinition path2Task1 = new TaskDefinition();
TaskDefinition path2Task2 = new TaskDefinition();

// Both paths depend on start
path1Task1.setDependsOn(List.of(start.getId()));
path2Task1.setDependsOn(List.of(start.getId()));

// Chain within paths
path1Task2.setDependsOn(List.of(path1Task1.getId()));
path2Task2.setDependsOn(List.of(path2Task1.getId()));

// Join paths
TaskDefinition join = new TaskDefinition();
join.setDependsOn(List.of(path1Task2.getId(), path2Task2.getId()));
```

### Conditional Workflow

Tasks execute based on conditions:

```java
TaskDefinition start = new TaskDefinition();

// Path selection tasks
TaskDefinition pathA = new TaskDefinition();
pathA.setDependsOn(List.of(start.getId()));
pathA.setConditionalExpression("${start.output.result == 'A'}");

TaskDefinition pathB = new TaskDefinition();
pathB.setDependsOn(List.of(start.getId()));
pathB.setConditionalExpression("${start.output.result == 'B'}");

// Merge paths
TaskDefinition end = new TaskDefinition();
end.setDependsOn(List.of(pathA.getId(), pathB.getId()));
```

## User Review Points

Tasks can require user review using the `requireUserReview` field. When a task with this flag is encountered, the workflow pauses until user review is submitted.

```java
TaskDefinition task = new TaskDefinition();
task.setName("approval-task");
task.setRequireUserReview(true);
```

## Best Practices

1. **Avoid Circular Dependencies**: Ensure there are no circular dependencies between tasks.

2. **Group Related Tasks**: Use execution groups to organize related tasks and control parallel execution.

3. **Use Conditional Expressions**: Control task execution with conditions that depend on previous task outputs.

4. **Implement Error Handlers**: Set up error handling paths with `nextTaskOnFailure`.

5. **Start with Simple Patterns**: Begin with simpler patterns and move to more complex ones as needed.

6. **Test Incrementally**: Test your workflow with smaller subsets of tasks before adding more complexity.

7. **Monitor Execution**: Use the workflow monitoring API to track execution progress and troubleshoot issues.

## Example: ETL Workflow

Here's a complete example of an ETL (Extract, Transform, Load) workflow with parallel extract and transform steps, followed by sequential processing:

```java
// Create workflow definition
WorkflowDefinition workflow = new WorkflowDefinition();
workflow.setName("etl-data-pipeline");
workflow.setDescription("Extract-Transform-Load Data Pipeline");
workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.HYBRID);

// Extract tasks (can run in parallel)
TaskDefinition extractCustomers = createExtractTask("extract-customers");
TaskDefinition extractOrders = createExtractTask("extract-orders");
TaskDefinition extractProducts = createExtractTask("extract-products");

// Transform tasks (depend on extract tasks)
TaskDefinition transformCustomers = createTransformTask("transform-customers");
transformCustomers.setDependsOn(List.of(extractCustomers.getId()));

TaskDefinition transformOrders = createTransformTask("transform-orders");
transformOrders.setDependsOn(List.of(extractOrders.getId()));

TaskDefinition transformProducts = createTransformTask("transform-products");
transformProducts.setDependsOn(List.of(extractProducts.getId()));

// Join task (depends on all transform tasks)
TaskDefinition joinData = createJoinTask("join-data");
joinData.setDependsOn(List.of(
    transformCustomers.getId(),
    transformOrders.getId(),
    transformProducts.getId()
));

// Validation task (depends on join)
TaskDefinition validateData = createValidationTask("validate-data");
validateData.setDependsOn(List.of(joinData.getId()));

// Load task (depends on validation)
TaskDefinition loadData = createLoadTask("load-data");
loadData.setDependsOn(List.of(validateData.getId()));
loadData.setConditionalExpression("${validateData.output.success}");

// Add all tasks to workflow
List<TaskDefinition> tasks = new ArrayList<>();
tasks.add(extractCustomers);
tasks.add(extractOrders);
tasks.add(extractProducts);
tasks.add(transformCustomers);
tasks.add(transformOrders);
tasks.add(transformProducts);
tasks.add(joinData);
tasks.add(validateData);
tasks.add(loadData);

workflow.setTasks(tasks);

// Save the workflow definition
workflowService.createWorkflowDefinition(workflow);
```

## API Reference

### TaskDefinition

| Field                  | Type                  | Description                                                |
|------------------------|------------------------|------------------------------------------------------------|
| `id`                   | `Long`                 | Unique task identifier                                     |
| `name`                 | `String`               | Task name                                                 |
| `description`          | `String`               | Task description                                          |
| `type`                 | `String`               | Task type (http, database, etc.)                          |
| `executionOrder`       | `Integer`              | Execution order (for backward compatibility)              |
| `configuration`        | `Map<String, String>`  | Task configuration parameters                             |
| `retryLimit`           | `Integer`              | Maximum number of retry attempts                          |
| `timeoutSeconds`       | `Integer`              | Task execution timeout in seconds                         |
| `executionMode`        | `ExecutionMode`        | Execution mode (API, RABBITMQ)                            |
| `requireUserReview`    | `boolean`              | Whether this task requires user review                    |
| `conditionalExpression`| `String`               | Expression that determines if task should execute         |
| `nextTaskOnSuccess`    | `Long`                 | ID of task to execute on success                          |
| `nextTaskOnFailure`    | `Long`                 | ID of task to execute on failure                          |
| `dependsOn`            | `List<Long>`           | List of task IDs this task depends on                     |
| `executionGroup`       | `String`               | Group for parallel/sequential execution                   |
| `parallelExecution`    | `boolean`              | Whether this task can execute in parallel within its group|

### WorkflowDefinition

| Field                  | Type                        | Description                                   |
|------------------------|----------------------------|-----------------------------------------------|
| `id`                   | `Long`                     | Unique workflow identifier                     |
| `name`                 | `String`                   | Workflow name                                  |
| `description`          | `String`                   | Workflow description                           |
| `version`              | `String`                   | Workflow version                               |
| `createdAt`            | `LocalDateTime`            | Creation timestamp                             |
| `updatedAt`            | `LocalDateTime`            | Last update timestamp                          |
| `tasks`                | `List<TaskDefinition>`     | List of tasks in the workflow                  |
| `strategyType`         | `ExecutionStrategyType`    | Execution strategy type                        |

### ExecutionStrategyType

- `SEQUENTIAL`: Tasks execute one after another
- `PARALLEL`: Tasks execute in parallel where possible
- `CONDITIONAL`: Tasks execute based on conditions
- `GRAPH`: Tasks execute based on dependencies
- `HYBRID`: Combines dependencies and execution groups
